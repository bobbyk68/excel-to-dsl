// --- add these to your existing class ---

public java.util.regex.Pattern rx() {
    return this.regex; // your compiled Pattern field
}

public PatternIntrospector.Meta meta() {
    return this.meta;  // your parsed Meta (anchor scope, field key, etc.)
}

public String id() {
    return this.id;    // your atomic id
}


// ================= CSV EMIT (drop-in) =================

private static void emitRulesCsv(
        java.util.List<CompiledAtomic> compiled,
        java.util.List<RuleRow> rules,
        java.nio.file.Path outCsvPath,
        boolean append
) {
    // 0) compiled lookup: atomicId -> compiled
    java.util.Map<String, CompiledAtomic> byId = new java.util.HashMap<>();
    for (CompiledAtomic ca : compiled) byId.put(ca.id(), ca);

    // small “tables” kept in-memory
    record Impact(java.util.Set<String> rules, java.util.Set<String> errors) {}
    java.util.Map<String, java.util.Map<String, Impact>> impactByComboThenV1 = new java.util.HashMap<>();

    record Buf(String combo, String ruleId, String err, String v1, String v2, String rightClauseText,
               java.util.regex.Pattern rightRx) {}
    java.util.List<Buf> buffer = new java.util.ArrayList<>();

    // ---------- PHASE 1: COLLECT ----------
    for (RuleRow r : rules) {
        CompiledAtomic left  = byId.get(r.leftAtomicId());
        CompiledAtomic right = byId.get(r.rightAtomicId());

        // extract the two concrete values from the literal lines via your compiled regex
        String v1 = firstGroup(left  != null ? left.rx()  : null, r.leftLiteral());
        String v2 = firstGroup(right != null ? right.rx() : null, r.rightLiteral());

        // derive combo key (prefer row-provided; else from Meta of the two atomics)
        String combo = hasText(r.comboKey())
                ? r.comboKey()
                : deriveCombo(left != null ? left.meta() : null, right != null ? right.meta() : null);

        // register (combo, v1) -> rule & error (for pipe-joins later)
        impactByComboThenV1
                .computeIfAbsent(combo, k -> new java.util.HashMap<>())
                .computeIfAbsent(v1,    k -> new Impact(new java.util.LinkedHashSet<>(), new java.util.LinkedHashSet<>()))
                .rules.add(r.ruleId());
        impactByComboThenV1.get(combo).get(v1).errors.add(r.errorCode());

        // stash for phase 2
        buffer.add(new Buf(combo, r.ruleId(), r.errorCode(), v1, v2, r.rightClauseText(),
                right != null ? right.rx() : null));
    }

    // ---------- PHASE 2: EMIT ----------
    try {
        java.nio.file.Path p = outCsvPath;
        java.nio.file.Path parent = p.getParent();
        if (parent != null) java.nio.file.Files.createDirectories(parent);

        java.nio.file.OpenOption[] firstWrite = append
                ? new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND }
                : new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE };
        java.nio.file.OpenOption[] appendWrite = new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND };

        boolean firstRow = true;

        for (Buf b : buffer) {
            Impact imp = impactByComboThenV1
                    .getOrDefault(b.combo(), java.util.Map.of())
                    .getOrDefault(b.v1(),    new Impact(java.util.Set.of(), java.util.Set.of()));

            String rulesJoined  = joinPipe(imp.rules.isEmpty()  ? java.util.List.of(b.ruleId()) : imp.rules);
            String errorsJoined = joinPipe(imp.errors.isEmpty() ? java.util.List.of(b.err())    : imp.errors);

            // 1) Trigger row
            writeCsv(p, firstRow ? firstWrite : appendWrite, rulesJoined, errorsJoined, b.v1(), b.v2());
            firstRow = false;

            // 2) NONE row — generate a v2 that *fails only* the 2nd condition
            String negV2 = pickNegativeSecondValue(b.rightClauseText(), b.v2(), b.rightRx);
            writeCsv(p, appendWrite, "NONE", errorsJoined, b.v1(), negV2);
        }
    } catch (java.io.IOException ioe) {
        throw new RuntimeException("CSV emit failed for " + outCsvPath, ioe);
    }
}

// ================= helpers =================

private static boolean hasText(String s) { return s != null && !s.isBlank(); }

private static String firstGroup(java.util.regex.Pattern rx, String literal) {
    if (rx == null || !hasText(literal)) return "";
    var m = rx.matcher(literal);
    return (m.find() && m.groupCount() >= 1) ? nz(m.group(1)) : "";
}

private static String deriveCombo(PatternIntrospector.Meta left, PatternIntrospector.Meta right) {
    return abbrev(left) + "-" + abbrev(right);
}

private static String abbrev(PatternIntrospector.Meta m) {
    if (m == null) return "NA";
    String k = String.valueOf(m.fieldKey()).toUpperCase();
    if (k.contains("SP")) return "SP";
    if (k.contains("RP")) return "RP";
    if (k.contains("PP")) return "PP";
    if (k.contains("AD")) return "AD";
    if (k.contains("AI")) return "AI";
    if (k.contains("GI")) return "GI";
    return "NA";
}

/** Create a v2 that *does not* satisfy the right condition. Uses both the clause text and the right compiled regex. */
private static String pickNegativeSecondValue(String rightClauseText, String goodSecondValue, java.util.regex.Pattern rightRx) {
    // 1) If the clause contains a quoted set, choose an item outside that set.
    var set = new java.util.HashSet<String>();
    var q = java.util.regex.Pattern.compile("\"([^\"]+)\"")
            .matcher(rightClauseText == null ? "" : rightClauseText);
    while (q.find()) set.add(q.group(1));
    if (!set.isEmpty()) {
        for (String cand : new String[]{"ZZZ", "99X", "__NOT_IN_SET__"}) {
            if (!set.contains(cand) && !matchesRight(cand, rightRx)) return cand;
        }
    }

    // 2) Numeric comparisons: step just beyond threshold.
    var num = java.util.regex.Pattern
            .compile("(>=|<=|>|<|equals|==)\\s*(\\d+(?:\\.\\d+)?)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(rightClauseText == null ? "" : rightClauseText);
    if (num.find()) {
        String op = num.group(1), lit = num.group(2);
        try {
            double v = Double.parseDouble(lit);
            String cand =
                    (op.equals(">") || op.equals(">=")) ? String.valueOf(v - 1) :
                            (op.equals("<") || op.equals("<=")) ? String.valueOf(v + 1) :
                                    String.valueOf(v + 1); // equals / ==
            if (!matchesRight(cand, rightRx)) return cand;
        } catch (NumberFormatException ignore) { /* fall through */ }
    }

    // 3) Default: mutate the good value so equals/contains won't match, and verify against regex if present.
    String[] cands = new String[] {
            hasText(goodSecondValue) ? goodSecondValue + "_X" : "__NEG__",
            "ZZZ", "99X", "__NEG__"
    };
    for (String cand : cands) {
        if (!matchesRight(cand, rightRx)) return cand;
    }
    return "__NEG__";
}

private static boolean matchesRight(String candidate, java.util.regex.Pattern rightRx) {
    return rightRx != null && candidate != null && rightRx.matcher(candidate).matches();
}

private static String joinPipe(java.util.Collection<String> xs) { return String.join("|", xs); }

private static void writeCsv(java.nio.file.Path file,
                             java.nio.file.OpenOption[] opts,
                             String c1, String c2, String c3, String c4) throws java.io.IOException {
    String row = csv(c1) + "," + csv(c2) + "," + csv(c3) + "," + csv(c4) + "\n";
    java.nio.file.Files.writeString(file, row, java.nio.charset.StandardCharsets.UTF_8, opts);
}

private static String csv(String s) {
    if (s == null) s = "";
    boolean q = s.contains(",") || s.contains("\"") || s.contains("\n");
    String t = s.replace("\"", "\"\"");
    return q ? "\"" + t + "\"" : t;
}

private static String nz(String s) { return s == null ? "" : s; }



// choose your output file
java.nio.file.Path out = java.nio.file.Path.of("output/rules.csv");

// emit (deferred: complete co-triggers by combo + first value)
emitRulesCsv(compiled, rules, out, /*append*/ false);

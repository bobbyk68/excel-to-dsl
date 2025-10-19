//----------------------------------------------
// ADDED: expose compiled regex Pattern
//----------------------------------------------
public java.util.regex.Pattern rx() {
    return this.regex;   // your field from the screenshot
}

//----------------------------------------------
// ADDED: expose parsed Meta (anchor, fieldKey, operator, etc.)
//----------------------------------------------
public PatternIntrospector.Meta meta() {
    return this.meta;    // same as in your constructor
}

//----------------------------------------------
// ADDED: expose id (you already store it)
//----------------------------------------------
public String id() {
    return this.id;
}

// ===== Runner.java =====

// ----------------------------------------------
// 1) After you have `compiled` and your `rules` list, build an index by atomic id
// ----------------------------------------------
java.util.Map<String, CompiledAtomic> compiledById = new java.util.HashMap<>();
for (CompiledAtomic ca : compiled) {
        compiledById.put(ca.id(), ca);
        }

// ----------------------------------------------
// 2) Phase 1: COLLECT
//    - Build (comboKey, firstValue) -> Impact (ruleIds + errorCodes)
//    - Stash a light buffer per rule for Phase 2 emitting
// ----------------------------------------------
record Impact(java.util.Set<String> rules, java.util.Set<String> errors) {}
java.util.Map<String, java.util.Map<String, Impact>> impactByComboThenV1 = new java.util.HashMap<>();

record Buf(String combo, String ruleId, String err, String v1, String v2, String rightClauseText) {}
java.util.List<Buf> buffer = new java.util.ArrayList<>();

for (RuleRow r : rules) {
CompiledAtomic left  = compiledById.get(r.leftAtomicId());
CompiledAtomic right = compiledById.get(r.rightAtomicId());

// --- extract first and second values by applying the compiled regex to the literal lines
String v1 = firstGroup(left != null ? left.rx() : null,  r.leftLiteral());
String v2 = firstGroup(right != null ? right.rx() : null, r.rightLiteral());

// --- derive combo key (prefer your existing, else from Meta)
String combo = (hasText(r.comboKey()) ? r.comboKey() : deriveCombo(left != null ? left.meta() : null,
        right != null ? right.meta() : null));

// --- register into (combo, v1) bucket
    impactByComboThenV1
            .computeIfAbsent(combo, k -> new java.util.HashMap<>())
        .computeIfAbsent(v1,    k -> new Impact(new java.util.LinkedHashSet<>(), new java.util.LinkedHashSet<>()))
        .rules.add(r.ruleId());
        impactByComboThenV1.get(combo).get(v1).errors.add(r.errorCode());

        // --- stash for Phase 2
        buffer.add(new Buf(combo, r.ruleId(), r.errorCode(), v1, v2, r.rightClauseText()));
        }

// ----------------------------------------------
// 3) Phase 2: EMIT (after ALL rules processed)
//    - For each buffered rule, pipe-join only SAME-COMBO co-triggers
//    - Write two rows: trigger + NONE row
// ----------------------------------------------
java.nio.file.Path csv = java.nio.file.Path.of("output/rules.csv");
java.nio.file.Files.createDirectories(csv.getParent());
boolean append = false;
java.nio.file.OpenOption[] opts = append
        ? new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND }
        : new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE };

for (Buf b : buffer) {
Impact imp = impactByComboThenV1
        .getOrDefault(b.combo(), java.util.Map.of())
        .getOrDefault(b.v1(),    new Impact(java.util.Set.of(), java.util.Set.of()));

String rulesJoined  = joinPipe(imp.rules.isEmpty()  ? java.util.List.of(b.ruleId())   : imp.rules);
String errorsJoined = joinPipe(imp.errors.isEmpty() ? java.util.List.of(b.err())      : imp.errors);

// trigger row
writeCsv(csv, opts, rulesJoined, errorsJoined, b.v1(), b.v2());

// NONE row (break condition #2 only)
String neg = pickNegativeSecondValue(b.rightClauseText(), b.v2());
writeCsv(csv, opts, "NONE", errorsJoined, b.v1(), neg);
        }

// ----------------------------------------------
// helpers (add at bottom of Runner)
// ----------------------------------------------
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
    String key = String.valueOf(m.fieldKey()).toUpperCase();   // e.g., SP_CODE, RP_CODE, AD_TYPE_CODE …
    if (key.contains("SP")) return "SP";
    if (key.contains("RP")) return "RP";
    if (key.contains("PP")) return "PP";
    if (key.contains("AD")) return "AD";
    if (key.contains("AI")) return "AI";
    if (key.contains("GI")) return "GI";
    return "NA";
}

private static String pickNegativeSecondValue(String rightClause, String good) {
    // 1) If the right clause lists allowed values -> choose something outside
    var set = new java.util.HashSet<String>();
    var m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(nz(rightClause));
    while (m.find()) set.add(m.group(1));
    if (!set.isEmpty()) {
        for (String c : new String[]{"ZZZ","99X","__NOT_IN_SET__"})
            if (!set.contains(c)) return c;
    }
    // 2) Numeric nudge outside boundary
    var num = java.util.regex.Pattern
            .compile("(>=|<=|>|<|equals|==)\\s*(\\d+(?:\\.\\d+)?)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(nz(rightClause));
    if (num.find()) {
        String op = num.group(1), lit = num.group(2);
        try {
            double v = Double.parseDouble(lit);
            if (op.equals(">") || op.equals(">="))  return String.valueOf(v - 1);
            if (op.equals("<") || op.equals("<="))  return String.valueOf(v + 1);
            return String.valueOf(v + 1); // equals / ==
        } catch (NumberFormatException ignore) {}
    }
    // 3) Default mutate so 'equals' / 'contains' won’t pass
    return hasText(good) ? good + "_X" : "__NEG__";
}

private static String joinPipe(java.util.Collection<String> xs) {
    return String.join("|", xs);
}

private static String nz(String s) { return s == null ? "" : s; }

private static void writeCsv(java.nio.file.Path file,
                             java.nio.file.OpenOption[] opts,
                             String c1, String c2, String c3, String c4) {
    String row = csv(c1) + "," + csv(c2) + "," + csv(c3) + "," + csv(c4) + "\n";
    try { java.nio.file.Files.writeString(file, row, java.nio.charset.StandardCharsets.UTF_8, opts); }
    catch (java.io.IOException e) { throw new RuntimeException("CSV write failed: " + file, e); }
}

private static String csv(String s) {
    if (s == null) s = "";
    boolean q = s.contains(",") || s.contains("\"") || s.contains("\n");
    String t = s.replace("\"","\"\"");
    return q ? "\"" + t + "\"" : t;
}


// 1) load JSON → Bundle bundle
// 2) compile atomics
List<CompiledAtomic> compiled = bundle.atomic().stream()
        .map(a -> CompiledAtomic.compile(a.id(), "^" + a.pattern() + "$", a.pattern(), a.dsl()))
        .collect(java.util.stream.Collectors.toList());

// 3) build rule rows (your current flow)
List<RuleRow> rules = /* your existing list */;

// >>> ADD the block from section (B) here (index compiledById, collect, emit) <<<

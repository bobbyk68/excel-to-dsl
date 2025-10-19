// --- new fields ---
private String comboKey;     // e.g., "SP-SP", "RP-PP"
private String firstValue;   // v1 – value that triggers condition #1
private String secondValue;  // v2 – value that triggers condition #2

// --- new getters ---
public String comboKey()    { return comboKey; }
public String firstValue()  { return firstValue; }
public String secondValue() { return secondValue; }

// --- new setters ---
public void setComboKey(String k)  { this.comboKey = k; }
public void setFirstValue(String v){ this.firstValue = v; }
public void setSecondValue(String v){ this.secondValue = v; }


// 1) set the two triggering values directly from the hits
row.setFirstValue(  nz(leftHit  != null ? leftHit.rawValue()  : null));
        row.setSecondValue( nz(rightHit != null ? rightHit.rawValue() : null));

// 2) set the combo from the two clause metas (SP-SP, RP-PP, etc.)
        row.setComboKey( comboFrom(leftHit != null ? leftHit.meta() : null,
rightHit!= null ? rightHit.meta(): null) );

// 3) keep the right clause text for the NONE row generator
//    (you already have 'thenCondition' – we reuse that later)


private static String comboFrom(Object leftMeta, Object rightMeta) {
    return abbrev(leftMeta) + "-" + abbrev(rightMeta);
}

private static String abbrev(Object meta) {
    if (meta == null) return "NA";
    // PatternIntrospector.Meta has fieldKey(); reflect or call directly
    try {
        Object fieldKey = meta.getClass().getMethod("fieldKey").invoke(meta);
        String k = String.valueOf(fieldKey).toUpperCase();
        if (k.contains("SPECIAL") || k.contains("SP_") || k.contains("SP")) return "SP";
        if (k.contains("REQUESTED")|| k.contains("RP_") || k.contains("RP")) return "RP";
        if (k.contains("PREVIOUS") || k.contains("PP_") || k.contains("PP")) return "PP";
        if (k.contains("ADDITIONAL_DOCUMENT") || k.contains("AD_") || k.contains("AD")) return "AD";
        if (k.contains("ADDITIONAL_INFORMATION") || k.contains("AI_") || k.contains("AI")) return "AI";
        if (k.contains("GOODSITEM") || k.contains("GI")) return "GI";
    } catch (Exception ignore) { /* fall through */ }
    return "NA";
}

private static String nz(String s) { return s == null ? "" : s; }

private static void emitRulesCsv(
        java.util.List<RuleRow> rules,
        java.nio.file.Path outCsvPath,
        boolean append
) {
    // (comboKey, firstValue) -> { ruleIds, errorCodes }  (for pipe-joins)
    record Impact(java.util.Set<String> rules, java.util.Set<String> errors) {}
    java.util.Map<String, java.util.Map<String, Impact>> index = new java.util.HashMap<>();

    // per-rule buffer for the emit phase
    record Buf(String combo, String ruleId, String err, String v1, String v2, String rightClause) {}
    java.util.List<Buf> buf = new java.util.ArrayList<>();

    // Phase 1: collect
    for (RuleRow r : rules) {
        String combo = r.comboKey();
        String v1    = r.firstValue();
        String v2    = r.secondValue();
        String err   = r.errorCode();
        String rc    = r.thenCondition(); // used to craft the NONE v2

        index.computeIfAbsent(combo, k -> new java.util.HashMap<>())
                .computeIfAbsent(v1, k -> new Impact(new java.util.LinkedHashSet<>(), new java.util.LinkedHashSet<>()))
                .rules.add(r.id());
        index.get(combo).get(v1).errors.add(err);

        buf.add(new Buf(combo, r.id(), err, v1, v2, rc));
    }

    // Phase 2: emit (with full knowledge)
    try {
        var parent = outCsvPath.getParent();
        if (parent != null) java.nio.file.Files.createDirectories(parent);

        var firstWrite  = append
                ? new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND }
                : new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE };
        var appendWrite = new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND };

        boolean first = true;
        for (Buf b : buf) {
            Impact imp = index.getOrDefault(b.combo(), java.util.Map.of())
                    .getOrDefault(b.v1(), new Impact(java.util.Set.of(), java.util.Set.of()));

            String rulesJoined  = String.join("|", imp.rules.isEmpty()  ? java.util.List.of(b.ruleId()) : imp.rules);
            String errorsJoined = String.join("|", imp.errors.isEmpty() ? java.util.List.of(b.err())    : imp.errors);

            // trigger row
            writeCsv(outCsvPath, first ? firstWrite : appendWrite, rulesJoined, errorsJoined, b.v1(), b.v2());
            first = false;

            // NONE row: keep v1, break only cond#2 using the right clause text
            String negV2 = pickNegativeSecondValue(b.rightClause(), b.v2());
            writeCsv(outCsvPath, appendWrite, "NONE", errorsJoined, b.v1(), negV2);
        }
    } catch (java.io.IOException e) {
        throw new RuntimeException("CSV emit failed: " + outCsvPath, e);
    }
}

private static String pickNegativeSecondValue(String rightClause, String good){
    var set = new java.util.HashSet<String>();
    var m = java.util.regex.Pattern.compile("\"([^\"]+)\"")
            .matcher(rightClause == null ? "" : rightClause);
    while (m.find()) set.add(m.group(1));
    if (!set.isEmpty()) {
        for (String c : new String[]{"ZZZ","99X","__NOT_IN_SET__"})
            if (!set.contains(c)) return c;
    }
    var num = java.util.regex.Pattern
            .compile("(>=|<=|>|<|equals|==)\\s*(\\d+(?:\\.\\d+)?)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(rightClause == null ? "" : rightClause);
    if (num.find()) {
        String op = num.group(1), lit = num.group(2);
        try {
            double v = Double.parseDouble(lit);
            if (op.equals(">") || op.equals(">="))  return String.valueOf(v - 1);
            if (op.equals("<") || op.equals("<="))  return String.valueOf(v + 1);
            return String.valueOf(v + 1);
        } catch (NumberFormatException ignore) { }
    }
    return (good == null || good.isBlank()) ? "__NEG__" : good + "_X";
}

private static void writeCsv(java.nio.file.Path file, java.nio.file.OpenOption[] opts,
                             String c1,String c2,String c3,String c4) throws java.io.IOException {
    String row = csv(c1)+","+csv(c2)+","+csv(c3)+","+csv(c4)+"\n";
    java.nio.file.Files.writeString(file, row, java.nio.charset.StandardCharsets.UTF_8, opts);
}
private static String csv(String s){
    if (s==null) s="";
    boolean q=s.contains(",")||s.contains("\"")||s.contains("\n");
    String t=s.replace("\"","\"\"");
    return q?("\""+t+"\""):t;
}




public void collectAll(/* your existing params */) {
    List<RuleRow> rows = new ArrayList<>();

    for (/* each Excel line or rule source */) {
        AtomicHit leftHit  = /* your existing match */;
        AtomicHit rightHit = /* your existing match */;

        RuleRow row = /* your existing build */;
        row.setFirstValue(nz(leftHit  != null ? leftHit.rawValue()  : null));
        row.setSecondValue(nz(rightHit != null ? rightHit.rawValue() : null));
        row.setComboKey(comboFrom(leftHit != null ? leftHit.meta() : null,
                rightHit!= null ? rightHit.meta(): null));

        rows.add(row);
    }

    // >>> CSV generation happens here (after all rows are known) <<<
    emitRulesCsv(rows, java.nio.file.Path.of("output/rules.csv"), /*append*/ false);
}



// Two-phase emitter using only RuleRow
private static void emitRulesCsv(List<RuleRow> rules, java.nio.file.Path outCsvPath, boolean append) {
    // (comboKey, firstValue) -> { ruleIds, errorCodes } for pipe-joins
    record Impact(Set<String> rules, Set<String> errors) {}
    Map<String, Map<String, Impact>> index = new HashMap<>();

    // Per-rule buffer (so we can emit after the index is complete)
    record Buf(String combo, String ruleId, String err, String v1, String v2, String rightClause) {}
    List<Buf> buf = new ArrayList<>();

    // Phase 1: collect
    for (RuleRow r : rules) {
        String combo = nz(r.comboKey());
        String v1    = nz(r.firstValue());
        String v2    = nz(r.secondValue());
        String err   = nz(r.errorCode());
        String rc    = nz(r.thenCondition()); // right clause text for NONE value generation

        index.computeIfAbsent(combo, k -> new HashMap<>())
                .computeIfAbsent(v1,    k -> new Impact(new LinkedHashSet<>(), new LinkedHashSet<>()))
                .rules.add(r.id());
        index.get(combo).get(v1).errors.add(err);

        buf.add(new Buf(combo, r.id(), err, v1, v2, rc));
    }

    // Phase 2: emit
    try {
        var parent = outCsvPath.getParent();
        if (parent != null) java.nio.file.Files.createDirectories(parent);

        var firstWrite  = append
                ? new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND }
                : new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE };
        var appendWrite = new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND };

        boolean first = true;
        for (Buf b : buf) {
            Impact imp = index.getOrDefault(b.combo(), Map.of())
                    .getOrDefault(b.v1(),   new Impact(Set.of(), Set.of()));
            String rulesJoined  = joinPipe(imp.rules.isEmpty()  ? List.of(b.ruleId()) : imp.rules);
            String errorsJoined = joinPipe(imp.errors.isEmpty() ? List.of(b.err())    : imp.errors);

            // Trigger row
            writeCsv(outCsvPath, first ? firstWrite : appendWrite, rulesJoined, errorsJoined, b.v1(), b.v2());
            first = false;

            // NONE row — keep v1, break only condition #2
            String negV2 = pickNegativeSecondValue(b.rightClause(), b.v2());
            writeCsv(outCsvPath, appendWrite, "NONE", errorsJoined, b.v1(), negV2);
        }
    } catch (java.io.IOException e) {
        throw new RuntimeException("CSV emit failed: " + outCsvPath, e);
    }
}

// combo from the two metas (e.g., SP-SP, RP-PP)
private static String comboFrom(Object leftMeta, Object rightMeta) {
    return abbrev(leftMeta) + "-" + abbrev(rightMeta);
}

private static String abbrev(Object meta) {
    if (meta == null) return "NA";
    try {
        Object fieldKey = meta.getClass().getMethod("fieldKey").invoke(meta);
        String k = String.valueOf(fieldKey).toUpperCase();
        if (k.contains("SPECIAL") || k.contains("SP_") || k.contains("SP")) return "SP";
        if (k.contains("REQUESTED")|| k.contains("RP_") || k.contains("RP")) return "RP";
        if (k.contains("PREVIOUS") || k.contains("PP_") || k.contains("PP")) return "PP";
        if (k.contains("ADDITIONAL_DOCUMENT") || k.contains("AD_") || k.contains("AD")) return "AD";
        if (k.contains("ADDITIONAL_INFORMATION") || k.contains("AI_") || k.contains("AI")) return "AI";
        if (k.contains("GOODSITEM") || k.contains("GI")) return "GI";
    } catch (Exception ignore) { }
    return "NA";
}

private static String pickNegativeSecondValue(String rightClause, String good){
    var set = new java.util.HashSet<String>();
    var m = java.util.regex.Pattern.compile("\"([^\"]+)\"")
            .matcher(rightClause == null ? "" : rightClause);
    while (m.find()) set.add(m.group(1));
    if (!set.isEmpty()) {
        for (String c : new String[]{"ZZZ","99X","__NOT_IN_SET__"})
            if (!set.contains(c)) return c;
    }
    var num = java.util.regex.Pattern
            .compile("(>=|<=|>|<|equals|==)\\s*(\\d+(?:\\.\\d+)?)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(rightClause == null ? "" : rightClause);
    if (num.find()) {
        String op = num.group(1), lit = num.group(2);
        try {
            double v = Double.parseDouble(lit);
            if (op.equals(">") || op.equals(">="))  return String.valueOf(v - 1);
            if (op.equals("<") || op.equals("<="))  return String.valueOf(v + 1);
            return String.valueOf(v + 1);
        } catch (NumberFormatException ignore) {}
    }
    return (good == null || good.isBlank()) ? "__NEG__" : good + "_X";
}

private static void writeCsv(java.nio.file.Path file, java.nio.file.OpenOption[] opts,
                             String c1,String c2,String c3,String c4) throws java.io.IOException {
    String row = csv(c1)+","+csv(c2)+","+csv(c3)+","+csv(c4)+"\n";
    java.nio.file.Files.writeString(file, row, java.nio.charset.StandardCharsets.UTF_8, opts);
}

private static String csv(String s){
    if (s==null) s="";
    boolean q=s.contains(",")||s.contains("\"")||s.contains("\n");
    String t=s.replace("\"","\"\"");
    return q?("\""+t+"\""):t;
}

private static String joinPipe(java.util.Collection<String> xs){ return String.join("|", xs); }
private static String nz(String s){ return s==null? "": s; }



// (comboKey, firstValue) -> Impact (sets of ruleIds & errorCodes)
record Key(String combo, String v1) {}

record Impact(java.util.Set<String> ruleIds, java.util.Set<String> errorCodes) {
    static Impact create() {
        return new Impact(new java.util.LinkedHashSet<>(), new java.util.LinkedHashSet<>());
    }
}

// Per-rule buffer for the emit phase
record Buf(String combo, String ruleId, String err, String v1, String v2, String rightClause) {}





Map<Key, Impact> index = new HashMap<>();
List<Buf> buffer = new ArrayList<>();

// Phase 1: collect
for (RuleRow r : rows) {
Key key = new Key(r.comboKey(), r.firstValue());
Impact imp = index.computeIfAbsent(key, k -> Impact.create());
  imp.ruleIds.add(r.id());
        imp.errorCodes.add(r.errorCode());

        buffer.add(new Buf(r.comboKey(), r.id(), r.errorCode(),
                     r.firstValue(), r.secondValue(), r.thenCondition()));
        }

// Phase 2: emit
Path out = Path.of("output/rules.csv");
Files.createDirectories(out.getParent());
OpenOption[] firstWrite  = { CREATE, TRUNCATE_EXISTING, WRITE };
OpenOption[] appendWrite = { CREATE, APPEND };
boolean first = true;

for (Buf b : buffer) {
Impact imp = index.getOrDefault(new Key(b.combo(), b.v1()),
        Impact.create());

String rulesJoined  = String.join("|", imp.ruleIds.isEmpty() ? List.of(b.ruleId()) : imp.ruleIds);
String errorsJoined = String.join("|", imp.errorCodes.isEmpty() ? List.of(b.err()) : imp.errorCodes);

// Trigger row
writeCsv(out, first ? firstWrite : appendWrite, rulesJoined, errorsJoined, b.v1(), b.v2());
first = false;

// NONE row — craft a v2 that fails only condition #2
String negV2 = pickNegativeSecondValue(b.rightClause(), b.v2());
writeCsv(out, appendWrite, "NONE", errorsJoined, b.v1(), negV2);
        }




static String pickNegativeSecondValue(String rightClause, String good) {
    var set = new java.util.HashSet<String>();
    var q = java.util.regex.Pattern.compile("\"([^\"]+)\"")
            .matcher(rightClause == null ? "" : rightClause);
    while (q.find()) set.add(q.group(1));
    if (!set.isEmpty()) {
        for (String cand : new String[]{"ZZZ","99X","__NOT_IN_SET__"})
            if (!set.contains(cand)) return cand;
    }
    var num = java.util.regex.Pattern
            .compile("(>=|<=|>|<|equals|==)\\s*(\\d+(?:\\.\\d+)?)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(rightClause == null ? "" : rightClause);
    if (num.find()) {
        String op = num.group(1), lit = num.group(2);
        try {
            double v = Double.parseDouble(lit);
            if (op.equals(">") || op.equals(">="))  return String.valueOf(v - 1);
            if (op.equals("<") || op.equals("<="))  return String.valueOf(v + 1);
            return String.valueOf(v + 1); // equals / ==
        } catch (NumberFormatException ignore) {}
    }
    return (good == null || good.isBlank()) ? "__NEG__" : good + "_X";
}

static void writeCsv(Path file, OpenOption[] opts,
                     String c1,String c2,String c3,String c4) {
    String row = csv(c1)+","+csv(c2)+","+csv(c3)+","+csv(c4)+"\n";
    try { Files.writeString(file, row, java.nio.charset.StandardCharsets.UTF_8, opts); }
    catch (java.io.IOException e) { throw new RuntimeException("CSV write failed: "+file, e); }
}

static String csv(String s) {
    if (s == null) s = "";
    boolean q = s.contains(",") || s.contains("\"") || s.contains("\n");
    String t = s.replace("\"","\"\"");
    return q ? "\"" + t + "\"" : t;
}


// Keyed by (comboKey, firstValue): who else co-triggers with me?
record Key(String combo, String v1) {}

record Impact(java.util.Set<String> ruleIds, java.util.Set<String> errorCodes) {
    static Impact create() {
        return new Impact(new java.util.LinkedHashSet<>(), new java.util.LinkedHashSet<>());
    }
}

// Per-rule buffer for phase 2 (emit)
record Buf(String combo, String ruleId, String err, String v1, String v2, String rightClause) {}




Map<Key, Impact> index = new java.util.HashMap<>();
List<Buf> buffer = new java.util.ArrayList<>();

for (/* each rule source */) {
// You already have these hits here
AtomicHit leftHit  = ...;  // has rawValue(), meta()
AtomicHit rightHit = ...;

RuleRow r = /* your current build */;

// 1) Enhance the row once (single source of truth)
    r.setFirstValue(  nz(leftHit  != null ? leftHit.rawValue()  : null));
        r.setSecondValue( nz(rightHit != null ? rightHit.rawValue() : null));
        r.setComboKey( comboFrom(leftHit  != null ? leftHit.meta()  : null,
rightHit != null ? rightHit.meta() : null) );

// 2) Add to the MAP (while we loop the rows)
Key key = new Key(r.comboKey(), r.firstValue());
Impact imp = index.computeIfAbsent(key, k -> Impact.create());
    imp.ruleIds.add(r.id());
        imp.errorCodes.add(r.errorCode());

        // 3) Buffer for emit (we’ll need rightClause to craft NONE v2)
        buffer.add(new Buf(r.comboKey(), r.id(), r.errorCode(),
                       r.firstValue(), r.secondValue(), r.thenCondition()));
        }




java.nio.file.Path out = java.nio.file.Path.of("output/rules.csv");
java.nio.file.Files.createDirectories(out.getParent());

var firstWrite  = new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
        java.nio.file.StandardOpenOption.WRITE };
var appendWrite = new java.nio.file.OpenOption[]{ java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND };
boolean first = true;

for (Buf b : buffer) {
Impact imp = index.getOrDefault(new Key(b.combo(), b.v1()), Impact.create());

String rulesJoined  = String.join("|", imp.ruleIds.isEmpty()  ? java.util.List.of(b.ruleId()) : imp.ruleIds);
String errorsJoined = String.join("|", imp.errorCodes.isEmpty()? java.util.List.of(b.err())    : imp.errorCodes);

// Trigger row
writeCsv(out, first ? firstWrite : appendWrite, rulesJoined, errorsJoined, b.v1(), b.v2());
first = false;

// NONE row — same v1, v2 that *fails only* condition #2
String negV2 = pickNegativeSecondValue(b.rightClause(), b.v2());
writeCsv(out, appendWrite, "NONE", errorsJoined, b.v1(), negV2);
        }



private static String comboFrom(Object leftMeta, Object rightMeta) {
    return abbrev(leftMeta) + "-" + abbrev(rightMeta);
}
private static String abbrev(Object meta) {
    if (meta == null) return "NA";
    try {
        Object fk = meta.getClass().getMethod("fieldKey").invoke(meta);
        String k = String.valueOf(fk).toUpperCase();
        if (k.contains("SP")) return "SP";
        if (k.contains("RP")) return "RP";
        if (k.contains("PP")) return "PP";
        if (k.contains("AD")) return "AD";
        if (k.contains("AI")) return "AI";
        if (k.contains("GI")) return "GI";
    } catch (Exception ignore) {}
    return "NA";
}
private static String pickNegativeSecondValue(String rightClause, String good){
    var set = new java.util.HashSet<String>();
    var m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(rightClause == null ? "" : rightClause);
    while (m.find()) set.add(m.group(1));
    if (!set.isEmpty()) {
        for (String c : new String[]{"ZZZ","99X","__NOT_IN_SET__"}) if (!set.contains(c)) return c;
    }
    var num = java.util.regex.Pattern.compile("(>=|<=|>|<|equals|==)\\s*(\\d+(?:\\.\\d+)?)",
            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(rightClause == null ? "" : rightClause);
    if (num.find()) {
        String op = num.group(1), lit = num.group(2);
        try {
            double v = Double.parseDouble(lit);
            if (op.equals(">") || op.equals(">="))  return String.valueOf(v - 1);
            if (op.equals("<") || op.equals("<="))  return String.valueOf(v + 1);
            return String.valueOf(v + 1);
        } catch (NumberFormatException ignore) {}
    }
    return (good == null || good.isBlank()) ? "__NEG__" : good + "_X";
}
private static void writeCsv(java.nio.file.Path file, java.nio.file.OpenOption[] opts,
                             String c1,String c2,String c3,String c4) {
    String row = csv(c1)+","+csv(c2)+","+csv(c3)+","+csv(c4)+"\n";
    try { java.nio.file.Files.writeString(file, row, java.nio.charset.StandardCharsets.UTF_8, opts); }
    catch (java.io.IOException e) { throw new RuntimeException("CSV write failed: "+file, e); }
}
private static String csv(String s){
    if (s==null) s="";
    boolean q=s.contains(",")||s.contains("\"")||s.contains("\n");
    String t=s.replace("\"","\"\"");
    return q?("\""+t+"\""):t;
}
private static String nz(String s){ return s==null ? "" : s; }


// Phase 1: index + buffer
Map<Key, Impact> index = new HashMap<>();
List<Buf> buffer = new ArrayList<>();

for (RuleRow r : rows) {
Key key = new Key(r.comboKey(), r.firstValue());
  index.computeIfAbsent(key, k -> Impact.create()).ruleIds.add(r.id());
        index.get(key).errorCodes.add(r.errorCode());
        buffer.add(new Buf(r.comboKey(), r.id(), r.errorCode(), r.firstValue(), r.secondValue(), r.thenCondition()));
        }

// Phase 2: emit
        for (Buf b : buffer) {
Impact imp = index.getOrDefault(new Key(b.combo(), b.v1()), Impact.create());
String rulesJoined  = String.join("|", imp.ruleIds.isEmpty()? List.of(b.ruleId()) : imp.ruleIds);
String errorsJoined = String.join("|", imp.errorCodes.isEmpty()? List.of(b.err()) : imp.errorCodes);
write(rulesJoined, errorsJoined, b.v1(), b.v2());
write("NONE", errorsJoined, b.v1(), pickNegativeSecondValue(b.rightClause(), b.v2()));
        }

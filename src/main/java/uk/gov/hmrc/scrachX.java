// Map many possible phrasings/paths to stable 2-letter codes.
private static String domainAbbrevFromClause(String clause) {
    if (clause == null) return "NA";
    String s = clause.toLowerCase();

    // Common field-path keywords (fast path)
    if (s.contains("goodsitem.specialprocedures") || s.contains("special procedure")) return "SP";
    if (s.contains("goodsitem.requestedprocedure") || s.contains("requested procedure")) return "RP";
    if (s.contains("goodsitem.previousprocedure") || s.contains("previous procedure")) return "PP";
    if (s.contains("goodsitem.additionaldocuments") || s.contains("additional document")) return "AD";
    if (s.contains("goodsitem.additionalinformation") || s.contains("additional information")) return "AI";
    if (s.contains("goodsitem.")) return "GI"; // generic GoodsItem fallback

    // Abbreviations that might appear in authoring
    if (s.matches(".*\\bsp\\b.*")) return "SP";
    if (s.matches(".*\\brp\\b.*")) return "RP";
    if (s.matches(".*\\bpp\\b.*")) return "PP";
    if (s.matches(".*\\bad\\b.*")) return "AD";
    if (s.matches(".*\\bai\\b.*")) return "AI";
    if (s.matches(".*\\bgi\\b.*")) return "GI";

    // Last resort: look for dotted paths and pick the last meaningful token
    java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("goodsitem\\.(\\w+)")
            .matcher(s);
    if (m.find()) {
        String last = m.group(1);
        if (last.contains("special"))      return "SP";
        if (last.contains("requested"))    return "RP";
        if (last.contains("previous"))     return "PP";
        if (last.contains("additionaldoc"))return "AD";
        if (last.contains("additionalinf"))return "AI";
        return "GI";
    }
    return "NA";
}

private static String comboFromClauses(String leftClause, String rightClause) {
    return domainAbbrevFromClause(leftClause) + "-" + domainAbbrevFromClause(rightClause);
}


// RuleRow.java (add once if not already present)
private String comboKey;
private String firstValue;
private String secondValue;

public String comboKey()    { return comboKey; }
public String firstValue()  { return firstValue; }
public String secondValue() { return secondValue; }

public void setComboKey(String k)      { this.comboKey = k; }
public void setFirstValue(String v)    { this.firstValue = v; }
public void setSecondValue(String v)   { this.secondValue = v; }


// You already have these:
String leftClauseText  = row.ifCondition();   // your left IF line
String rightClauseText = row.thenCondition(); // your right IF line (the second condition)

// Set values directly from your hits
row.setFirstValue(  leftHit  != null ? leftHit.rawValue()  : "" );
        row.setSecondValue( rightHit != null ? rightHit.rawValue() : "" );

// Compute combo from the clause text you already store
        row.setComboKey(comboFromClauses(leftClauseText, rightClauseText));




// Key & value types
record Key(String combo, String v1) {}
record Impact(java.util.Set<String> ruleIds, java.util.Set<String> errorCodes) {
    static Impact create() {
        return new Impact(new java.util.LinkedHashSet<>(), new java.util.LinkedHashSet<>());
    }
}
record Buf(String combo, String ruleId, String err, String v1, String v2, String rightClause) {}

java.util.Map<Key, Impact> index = new java.util.HashMap<>();
java.util.List<Buf> buffer = new java.util.ArrayList<>();

// Inside your loop in collectAll after enhancing the row:
Key key = new Key(row.comboKey(), row.firstValue());
Impact imp = index.computeIfAbsent(key, k -> Impact.create());
imp.ruleIds.add(row.id());
        imp.errorCodes.add(row.errorCode());

// stash for emit pass
        buffer.add(new Buf(row.comboKey(), row.id(), row.errorCode(),
                   row.firstValue(), row.secondValue(), row.thenCondition()));




java.nio.file.Path out = java.nio.file.Path.of("output/rules.csv");
java.nio.file.Files.createDirectories(out.getParent());

java.nio.file.OpenOption[] firstWrite  = new java.nio.file.OpenOption[]{
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
        java.nio.file.StandardOpenOption.WRITE
};
java.nio.file.OpenOption[] appendWrite = new java.nio.file.OpenOption[]{
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND
};

boolean first = true;
for (Buf b : buffer) {
Impact imp = index.getOrDefault(new Key(b.combo(), b.v1()), Impact.create());

String rulesJoined  = String.join("|", imp.ruleIds.isEmpty()  ? java.util.List.of(b.ruleId()) : imp.ruleIds);
String errorsJoined = String.join("|", imp.errorCodes.isEmpty()? java.util.List.of(b.err())    : imp.errorCodes);

// Trigger row
writeCsv(out, first ? firstWrite : appendWrite, rulesJoined, errorsJoined, b.v1(), b.v2());
first = false;

// NONE row — keep v1, make v2 fail only condition #2
String negV2 = pickNegativeSecondValue(b.rightClause(), b.v2());
writeCsv(out, appendWrite, "NONE", errorsJoined, b.v1(), negV2);
        }



private static void writeCsv(java.nio.file.Path file, java.nio.file.OpenOption[] opts,
                             String c1,String c2,String c3,String c4) {
    String row = csv(c1)+","+csv(c2)+","+csv(c3)+","+csv(c4)+"\n";
    try { java.nio.file.Files.writeString(file, row, java.nio.charset.StandardCharsets.UTF_8, opts); }
    catch (java.io.IOException e) { throw new RuntimeException("CSV write failed: "+file, e); }
}
private static String csv(String s) {
    if (s == null) s = "";
    boolean q = s.contains(",") || s.contains("\"") || s.contains("\n");
    String t = s.replace("\"","\"\"");
    return q ? "\"" + t + "\"" : t;
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

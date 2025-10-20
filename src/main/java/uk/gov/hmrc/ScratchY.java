// Choose ONE element from a list-like right clause/value.
// Priority: first quoted token → first token after operators → split delimiters → fallback to raw.
private static String selectOneSecondValue(String rightClauseText, String rawSecondValue) {
    // 1) Quoted items: "W1", "W2", ...
    java.util.regex.Matcher q = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(nz(rightClauseText));
    if (q.find()) return q.group(1).trim();

    // 2) After operators: equals/in/one of/contains/==/!=
    java.util.regex.Matcher op = java.util.regex.Pattern
            .compile("(?:equals|one\\s+of|in|contains|==|!=)\\s*([A-Za-z0-9._-]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(nz(rightClauseText));
    if (op.find()) return op.group(1).trim();

    // 3) If rawSecondValue looks like a list, pick the first token
    String first = firstToken(rawSecondValue);
    if (!first.isBlank()) return first;

    // 4) Fallback
    return nz(rawSecondValue).trim();
}

private static String firstToken(String s) {
    s = nz(s).trim();
    if (s.isEmpty()) return "";
    // JSON-ish array: ["W1","W2"]
    java.util.regex.Matcher jq = java.util.regex.Pattern.compile("\\[\\s*\"([^\"]+)\"").matcher(s);
    if (jq.find()) return jq.group(1).trim();
    // CSV / pipe / space delimited: W1,W2 or W1|W2 or W1 W2
    for (String delim : new String[]{",","\\|","\\s"}) {
        String[] parts = s.split(delim);
        if (parts.length > 1) return parts[0].trim().replaceAll("^\"|\"$", "");
    }
    return s.replaceAll("^\"|\"$", "");
}

private static String nz(String s){ return s==null ? "" : s; }

// existing:
row.setFirstValue( leftHit  != null ? leftHit.rawValue()  : "" );

// NEW: pick a single element for the second value (v2)
String v2 = selectOneSecondValue(row.thenCondition(), rightHit != null ? rightHit.rawValue() : "");
row.setSecondValue(v2);

// combo as you already do (Option B):
row.setComboKey(comboFromClauses(row.ifCondition(), row.thenCondition()));

// Returns a normalised set of allowed tokens from a clause line or a raw hit.
// e.g.,  one of "D01","D02","D03"  -> {D01,D02,D03}
//        equals "D19"              -> {D19}
//        rawHit = "D19"            -> {D19}
private static java.util.Set<String> allowedSet(String clauseText, String rawHit) {
    java.util.Set<String> out = new java.util.LinkedHashSet<>();
    String t = clauseText == null ? "" : clauseText;

    // 1) quoted tokens
    var q = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(t);
    while (q.find()) out.add(normVal(q.group(1)));

    if (!out.isEmpty()) return out;

    // 2) single token after operator
    var m = java.util.regex.Pattern
            .compile("(?:equals|one\\s+of|in|contains|==|!=)\\s*([A-Za-z0-9._-]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(t);
    if (m.find()) { out.add(normVal(m.group(1))); return out; }

    // 3) fallback to rawHit (singleton)
    if (rawHit != null && !rawHit.isBlank()) out.add(normVal(rawHit));
    return out;
}




// ---- existing normalised values ----
String comboN = normCombo(row.comboKey());
String v1N    = normVal(row.firstValue());     // single left trigger value
String v2N    = normVal(row.secondValue());    // single chosen display value for CSV

// ---- Index A (all combos): by v1 ----
Impact impA = indexByV1.computeIfAbsent(new KeyV1(comboN, v1N), k -> Impact.create());
impA.ruleIds.add(row.id());
        impA.errorCodes.add(row.errorCode());

// ---- Index B (SP-SP only): by unordered *pair*, but using ALL allowed values ----
String pairKeyForBuffer = null; // we’ll still store a canonical key for *this* row’s output values
if ("SP-SP".equals(comboN)) {
// Build allowed sets from the two clause texts/raw hits
java.util.Set<String> leftSet  = allowedSet(row.ifCondition(),   v1N);
java.util.Set<String> rightSet = allowedSet(row.thenCondition(), v2N);

// Register every canonical pair from L × R
    for (String l : leftSet) {
        for (String r : rightSet) {
String canon = canonicalPair(l, r);     // e.g., D01|D19
            if (canon != null) {
Impact impB = indexPair.computeIfAbsent(new KeyPair(comboN, canon), k -> Impact.create());
                impB.ruleIds.add(row.id());
        impB.errorCodes.add(row.errorCode());
        }
        }
        }
// Keep a canonical key for THIS row’s display pair so emit can look up once
pairKeyForBuffer = canonicalPair(v1N, v2N);
}

// ---- Buffer for emit (unchanged, but include pairKey for SP-SP) ----
        buffer.add(new Buf(comboN, row.id(), row.errorCode(), v1N, v2N, pairKeyForBuffer, row.thenCondition()));



Impact imp = "SP-SP".equals(b.comboN())
        ? indexPair.getOrDefault(new KeyPair(b.comboN(), b.pairKey()), Impact.create())
        : indexByV1.getOrDefault(new KeyV1(b.comboN(), b.v1N()),      Impact.create());

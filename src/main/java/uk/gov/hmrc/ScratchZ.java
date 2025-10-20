private static boolean isSymmetricCombo(String comboKey) {
    if (comboKey == null) return false;
    String[] parts = comboKey.trim().toUpperCase().split("\\s*-\\s*");
    return parts.length == 2 && parts[0].equals(parts[1]); // e.g., SP-SP, RP-RP, AD-AD
}


// (comboKey, firstValue) -> Impact
record Key(String combo, String v1) {}
record Impact(java.util.Set<String> ruleIds, java.util.Set<String> errorCodes) {
    static Impact create() { return new Impact(new java.util.LinkedHashSet<>(), new java.util.LinkedHashSet<>()); }
}

private static void register(Map<Key, Impact> index, String combo, String value, String ruleId, String err) {
    Key k = new Key(combo, value);
    Impact imp = index.computeIfAbsent(k, kk -> Impact.create());
    imp.ruleIds.add(ruleId);
    imp.errorCodes.add(err);
}

// ---- inside your loop over enhanced RuleRows ----
String combo = normCombo(row.comboKey());
String v1    = normVal(row.firstValue());             // e.g., "C10"
String v2raw = normVal(row.secondValue());            // may be list-y before you applied selectOneSecondValue
String v2    = v2raw;                                 // ensure you already called selectOneSecondValue earlier

// 1) always index by v1
register(index, combo, v1, row.id(), row.errorCode());

// 2) for symmetric combos (SP-SP, RP-RP, AD-AD, ...), ALSO index by v2
        if (isSymmetricCombo(combo) && !v2.isBlank()) {
register(index, combo, v2, row.id(), row.errorCode());
        }

// 3) buffer as before for emit
        buffer.add(new Buf(combo, row.id(), row.errorCode(), v1, v2, row.thenCondition()));



private static String normCombo(String s){ return s==null? "NA-NA" : s.trim().toUpperCase(); }
private static String normVal(String s){ return s==null? "" : s.trim().toUpperCase(); }

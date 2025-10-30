/*
 * Module: rules-emitter
 * File: WhenPatternMatcher.collectAll (method)
 * @version: 1.7.3  (release: 2025-10-30)
 * @since:   1.0.0
 * Owner:    BK/RK
 *
 * Summary:
 * Build EmitContext directly from RuleRow (comboKey, firstValue, mergedThenCodes, errorCode).
 * No external helpers. Paths chosen by comboKey; operators default to "==" unless THEN is negated.
 */

public void collectAll(java.util.List<RuleRow> rows,
                       EmitterRegistry registry,
                       DslrFileWriter dsl) {

    for (RuleRow row : rows) {

        // 0) Canonical paths from comboKey (minimal, extendable in-place)
        //    We emit only the LAST segment later, so use slash/segment style here.
        final String combo = safe(row.comboKey()); // e.g. "SP-SP", "RP-PP", "AD-SP", "GI-SP"
        String ifPath, thenPath;

        switch (combo) {
            case "RP-PP" -> {
                ifPath   = "goodsItem/previousProcedure/code";
                thenPath = "goodsItem/requestedProcedure/code";
            }
            case "SP-SP" -> {
                ifPath   = "goodsItem/specialProcedure/code";
                thenPath = "goodsItem/specialProcedure/code";
            }
            case "GI-SP", "GI-PP", "GI-RP" -> {
                ifPath   = "goodsItem/exists"; // anchor-only type IF (exists)
                thenPath = switch (combo) {
                    case "GI-SP" -> "goodsItem/specialProcedure/code";
                    case "GI-PP" -> "goodsItem/requestedProcedure/code";
                    default      -> "goodsItem/previousProcedure/code";
                };
            }
            case "AD-SP" -> {
                // If your THEN is SP and IF is Additional Document (type or code) use param to choose.
                boolean useType = row.param() != null && row.param().toLowerCase().contains("type");
                ifPath   = "goodsItem/additionalDocument/" + (useType ? "type" : "code");
                thenPath = "goodsItem/specialProcedure/code";
            }
            case "AD-PP" -> {
                boolean useType = row.param() != null && row.param().toLowerCase().contains("type");
                ifPath   = "goodsItem/additionalDocument/" + (useType ? "type" : "code");
                thenPath = "goodsItem/requestedProcedure/code";
            }
            default -> {
                // Safe generic: keep anchor at GoodsItem; print last segments later.
                ifPath   = "goodsItem/unknownLhs";
                thenPath = "goodsItem/unknownRhs";
            }
        }

        // 1) Operators — keep simple and canonical
        String ifOp   = ifPath.endsWith("/exists") ? "exists" : "==";
        String thenOp = "==";

        // 2) Values — IF takes firstValue (single), THEN takes mergedThenCodes (list)
        java.util.List<String> ifVals =
                ifOp.equals("exists") ? java.util.List.of()
                        : (row.firstValue() != null && !row.firstValue().isBlank()
                        ? java.util.List.of(row.firstValue().trim())
                        : java.util.List.of());

        java.util.List<String> thenVals =
                (row.mergedThenCodes() != null && !row.mergedThenCodes().isEmpty())
                        ? row.mergedThenCodes()
                        : csvToList(row.mergedThenCodesCsv()); // fallback if you sometimes store CSV

        // 3) Flags
        boolean crossInstance = "SP-SP".equals(combo);       // two SP entries on the same GI
        boolean negateThen    = containsNegation(row.thenCondition()); // crude but effective for now
        boolean mergeable     = row.mergeGroupSize() > 1;    // you already track grouping count

        // 4) Error codes (allow duplicates → join with pipes in emitter)
        java.util.List<String> errorCodes =
                row.errorCode() == null || row.errorCode().isBlank()
                        ? java.util.List.of()
                        : java.util.List.of(row.errorCode().trim());

        // 5) Build Signature → EmitContext (nested clauses)
        Signature sig = new Signature(
                ifPath, ifOp, ifVals,
                thenPath, thenOp, thenVals,
                mergeable, crossInstance, negateThen,
                errorCodes,
                listToCsv(row.procedureCategory()),  // lightweight pass-through
                listToCsv(row.declarationType())
        );

        EmitContext.Clause ifC   = new EmitContext.Clause(sig.ifPath(),   sig.ifOpSymbol(),   sig.ifValues());
        EmitContext.Clause thenC = new EmitContext.Clause(sig.thenPath(), sig.thenOpSymbol(), sig.thenValues());

        EmitContext ctx = new EmitContext(
                row.id(),                                 // business rule id
                sig.procCategory(),                       // already CSV'd
                sig.decType(),
                ifC, thenC,
                sig.negateThen(),
                sig.errorCodes(),
                sig.mergeable(),
                sig.crossInstance()
        );

        registry.emit(ctx, dsl);
    }
}

// --------------------- tiny local utils (no external deps) ---------------------

private static String safe(String s){ return s == null ? "" : s.trim(); }

private static java.util.List<String> csvToList(String csv){
    if (csv == null || csv.isBlank()) return java.util.List.of();
    String[] parts = csv.split("\\s*,\\s*");
    java.util.List<String> out = new java.util.ArrayList<>(parts.length);
    for (String p : parts) if (!p.isBlank()) out.add(p.trim());
    return java.util.List.copyOf(out);
}

private static String listToCsv(java.util.List<String> list){
    if (list == null || list.isEmpty()) return "";
    return String.join(",", list);
}

private static boolean containsNegation(String english){
    if (english == null) return false;
    String s = english.toLowerCase();
    return s.contains("must not") || s.contains("should not") || s.contains("not ");
}

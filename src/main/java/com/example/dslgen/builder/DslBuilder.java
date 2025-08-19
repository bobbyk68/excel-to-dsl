// Inside uk.gov.h.builder.DslBuilder

/** Aggregate result for a whole spreadsheet/job. */
public static class ResultAll {
    /** De-duped DSL dictionary lines (global). */
    public final List<String> dslLines = new ArrayList<>();
    /** DSLR lines grouped by BR prefix, e.g. "BR675" -> lines. */
    public final Map<String, List<String>> dslrByBr = new LinkedHashMap<>();
}

/** Back-compat wrapper: build for a list of rows, aggregate, and de-dupe. */
public ResultAll build(List<uk.gov.h.model.RuleRow> rows) {
    ResultAll all = new ResultAll();

    // De-dupe DSL by LHS to avoid duplicates across rules
    Map<String, String> dslDict = new LinkedHashMap<>();

    for (uk.gov.h.model.RuleRow row : rows) {
        Result r = build(row); // <- your existing per-rule method

        // collect DSL: expect lines like "[when] LHS = RHS" / "[then] LHS = RHS"
        for (String line : r.dslLines) {
            int idx = line.indexOf("] ");
            int eq  = line.indexOf(" = ");
            if (idx > 0 && eq > idx) {
                String lhs = line.substring(idx + 2, eq).trim();
                String rhs = line.substring(eq + 3).trim();
                dslDict.putIfAbsent(lhs, rhs);
            }
        }

        // group DSLR by BR prefix
        String br = extractBrPrefix(row.getName()); // e.g. "BR675"
        all.dslrByBr.computeIfAbsent(br, k -> new ArrayList<>()).addAll(r.dslrLines);
    }

    // Rebuild DSL lines in a stable order
    dslDict.forEach((lhs, rhs) -> all.dslLines.add("[when] " + lhs + " = " + rhs));

    return all;
}

// Helper (kept simple; adapt if your naming varies)
private static String extractBrPrefix(String ruleName) {
    // e.g. "BR675_1791_U110_is_missing" -> "BR675"
    if (ruleName == null) return "BR";
    int underscore = ruleName.indexOf('_');
    return underscore > 0 ? ruleName.substring(0, underscore) : ruleName;
}
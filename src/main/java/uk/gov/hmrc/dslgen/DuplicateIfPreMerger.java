package uk.gov.hmrc.dslgen;

public final class DuplicateIfPreMerger {

    public static Result mergeByRawIf(java.util.List<RuleRow> rows) {
        java.util.Map<String, RuleRow> keeperByIf = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.LinkedHashSet<String>> codesByIf = new java.util.HashMap<>();
        java.util.List<RuleRow> removed = new java.util.ArrayList<>();

        for (RuleRow row : rows) {
            String ifKey = normalize(row.ifCondition()); // KEY = full raw IF (incl. value)

            RuleRow keeper = keeperByIf.get(ifKey);
            if (keeper == null) {
                keeper = row;                     // keep FIRST occurrence
                keeper.ensureMergedThenCodes();
                keeperByIf.put(ifKey, keeper);
                codesByIf.put(ifKey, new java.util.LinkedHashSet<>());
            } else {
                removed.add(row);                 // mark duplicate for removal
            }

            // Extract code(s) from the raw Excel THEN text and collect them
            for (String code : extractThenCodes(row.thenCondition())) {
                if (!code.isBlank()) codesByIf.get(ifKey).add(code);
            }
        }

        // Write merged codes back to the kept rows
        for (java.util.Map.Entry<String, RuleRow> e : keeperByIf.entrySet()) {
            java.util.LinkedHashSet<String> uniq = codesByIf.get(e.getKey());
            java.util.List<String> merged = new java.util.ArrayList<>();
            if (uniq != null) merged.addAll(uniq);

            RuleRow r = e.getValue();
            r.mergedThenCodes().clear();
            r.mergedThenCodes().addAll(merged);
            r.setMergedThenCodesCsv(String.join(",", merged)); // e.g., "A1,A2,A7"
        }

        return new Result(new java.util.ArrayList<>(keeperByIf.values()), removed);
    }

    // --- THEN code extractor ---
    // Handles: "only one VG1" -> VG1; "only in VG1,V,CX" -> VG1,V,CX; "allowed in A1" -> A1; parentheses/quotes ok.
    private static java.util.List<String> extractThenCodes(String rawThen) {
        if (rawThen == null) return java.util.List.of();
        String s = normalize(stripSmartQuotes(rawThen));

        int pIn = lastIndexIgnoreCase(s, " in ");
        int pTo = lastIndexIgnoreCase(s, " to ");
        int pWith = lastIndexIgnoreCase(s, " with ");
        int pivot = Math.max(pIn, Math.max(pTo, pWith));

        String tail = (pivot >= 0) ? s.substring(pivot + 4).trim() : s;
        if (tail.startsWith("(") && tail.endsWith(")") && tail.length() >= 2) {
            tail = tail.substring(1, tail.length() - 1).trim();
        }

        if (tail.contains(",")) {
            String[] parts = tail.split("\\s*,\\s*");
            java.util.List<String> out = new java.util.ArrayList<>(parts.length);
            for (String p : parts) {
                String t = sanitize(stripQuotes(p));
                if (!t.isBlank()) out.add(t);
            }
            return out;
        }

        String token = sanitize(stripQuotes(lastCodeLikeToken(tail)));
        if (token.isBlank()) token = sanitize(stripQuotes(lastCodeLikeToken(s)));
        return token.isBlank() ? java.util.List.of() : java.util.List.of(token);
    }

    // --- helpers ---
    private static String normalize(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }
    private static String stripQuotes(String s) {
        String t = s.trim();
        if ((t.startsWith("'") && t.endsWith("'")) || (t.startsWith("\"") && t.endsWith("\""))) {
            return t.substring(1, t.length() - 1).trim();
        }
        return t;
    }
    private static String stripSmartQuotes(String s) {
        return s.replace('‘','\'').replace('’','\'').replace('“','"').replace('”','"');
    }
    private static String lastCodeLikeToken(String s) {
        String[] parts = s.trim().split("\\s+");
        for (int i = parts.length - 1; i >= 0; i--) {
            String t = sanitize(parts[i]);
            if (!t.isBlank()) return t;
        }
        return "";
    }
    private static String sanitize(String token) { return token.replaceAll("[^A-Za-z0-9_-]", ""); }
    private static int lastIndexIgnoreCase(String s, String needle) { return s.toLowerCase().lastIndexOf(needle.trim().toLowerCase()); }

    private DuplicateIfPreMerger() {}

    public static final class Result {
        private final java.util.List<RuleRow> kept;     // unique IF rows (first occurrence, mutated)
        private final java.util.List<RuleRow> removed;  // duplicates (dropped)

        public Result(java.util.List<RuleRow> kept, java.util.List<RuleRow> removed) {
            this.kept = kept;
            this.removed = removed;
        }
        public java.util.List<RuleRow> kept() { return kept; }
        public java.util.List<RuleRow> removed() { return removed; }
    }
}

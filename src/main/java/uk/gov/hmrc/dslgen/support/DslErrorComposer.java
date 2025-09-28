package uk.gov.hmrc.dslgen.support;

public final class DslErrorComposer {

    private DslErrorComposer() {}

    /**
     * Compose a short, human-readable error from raw "when" lines.
     * - Ignores detailed codes (e.g., "with code equals \"50P\"").
     * - Matches high-level phrases via config-driven patterns.
     * - Joins with the configured joinWord (default "and").
     * - No commas are ever used.
     */
    public static String compose(String ruleId, java.util.List<String> whenLines) {
        DslErrorConfig cfg = DslErrorConfig.get();

        java.util.LinkedHashSet<String> tags = new java.util.LinkedHashSet<>();
        if (whenLines != null) {
            for (String raw : whenLines) {
                if (raw == null) continue;
                String line = raw.trim().toLowerCase();
                // Strip bullet marker if present
                if (line.startsWith("- ")) line = line.substring(2).trim();
                // Try each rule
                for (DslErrorConfig.PatternRule r : cfg.rules()) {
                    if (r.matches(line)) {
                        tags.add(r.tag());
                        // do not break: allow multiple rules to add distinct tags for a single line if needed
                    }
                }
            }
        }

        if (tags.isEmpty()) {
            return cfg.prefix() + "unspecified condition";
        }
        String body = String.join(" " + cfg.joinWord() + " ", tags);
        return cfg.prefix() + body;
    }
}

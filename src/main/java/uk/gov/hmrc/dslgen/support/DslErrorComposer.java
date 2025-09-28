package uk.gov.hmrc.dslgen.support;

// Builds: "Emit <ruleId> validation error for <clause1> and <clause2> and <clause3>."
// Input can be the raw "when" lines, including bullet lines starting with "-" that
// belong to the previous header line.
public final class DslErrorComposer {

    private DslErrorComposer() {}

    /**
     * Compose the error message from raw "when" lines.
     *
     * Example input lines:
     *   "Goods item with special procedure exists"
     *   "    - with code equals \"50P\""
     *   "No matching goods item with additional information exists"
     *   "    - with code equals \"GEN28\""
     *
     * Output:
     *   "Emit BR675 validation error for goods item with special procedure exists with code equals 50P and no matching goods item with additional information exists with code equals GEN28."
     */
    public static String compose(String ruleId, java.util.List<String> whenLines) {
        java.util.List<String> clauses = parseWhenClauses(whenLines);
        return composeFromClauses(ruleId, clauses);
    }

    /**
     * Compose the error message when you already have final DSLR-ready clause strings.
     * These will simply be joined with " and " (no commas).
     */
    public static String composeFromClauses(String ruleId, java.util.List<String> clauses) {
        if (clauses == null || clauses.isEmpty()) {
            return "Emit " + ruleId + " validation error for unspecified condition.";
        }
        // Normalise + dedupe while preserving order
        java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>();
        for (String c : clauses) {
            String n = normalize(c);
            if (!n.isEmpty()) uniq.add(n);
        }
        String body = String.join(" and ", uniq);
        // Lower-case the first letter of body to read naturally after the prefix
        body = lowercaseFirst(body);
        String msg = "Emit " + ruleId + " validation error for " + body;
        return msg.endsWith(".") ? msg : msg + ".";
    }

    // --- helpers ---

    // Parses raw "when" lines into flat clauses:
    //   Header line starts a new clause.
    //   Lines starting with "-" are appended to the current clause.
    private static java.util.List<String> parseWhenClauses(java.util.List<String> lines) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (lines == null) return out;

        String current = null;

        for (String raw : lines) {
            String line = stripQuotes(normalizeSpaces(raw));
            if (line.isEmpty()) continue;

            boolean isBullet = startsWithDash(line);

            if (!isBullet) {
                // Push previous clause if present
                if (current != null && !current.isBlank()) {
                    out.add(current.trim());
                }
                current = toSentenceCase(line); // start a new clause
            } else {
                // Append bullet text to current clause
                String bullet = stripLeadingDash(line);
                if (bullet.startsWith("with ")) {
                    // reads nicely: "... exists with code equals 50P"
                    current = (current == null ? "" : current + " ") + bullet;
                } else {
                    // fallback joiner
                    current = (current == null ? "" : current + " ") + bullet;
                }
            }
        }
        if (current != null && !current.isBlank()) {
            out.add(current.trim());
        }
        // Lower-case initial word for natural flow after the prefix
        java.util.List<String> lowered = new java.util.ArrayList<>(out.size());
        for (String c : out) lowered.add(lowercaseFirst(c));
        return lowered;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        // collapse whitespace, remove surrounding quotes inside tokens like "50P"
        String t = stripQuotes(s.replaceAll("\\s+", " ").trim());
        return t;
    }

    private static String stripQuotes(String s) {
        // remove any simple wrapping double quotes around tokens (e.g., "GEN28" -> GEN28)
        return s.replace("\"", "");
    }

    private static String normalizeSpaces(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static boolean startsWithDash(String s) {
        // treat "- ..." as bullet, allowing leading spaces already normalised
        return s.startsWith("- ");
    }

    private static String stripLeadingDash(String s) {
        return s.startsWith("- ") ? s.substring(2).trim() : s;
    }

    private static String lowercaseFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String toSentenceCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

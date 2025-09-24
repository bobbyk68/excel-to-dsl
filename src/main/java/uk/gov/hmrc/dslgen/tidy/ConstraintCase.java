public enum ConstraintCase {
    // Canonical names
    ALL_OF,          // universal: "all ... must ..."
    AT_LEAST_ONE,    // existential: "at least one ... must ..."
    NONE,            // prohibition: "none ... must ..."
    EXACTLY_ONE,     // cardinality: "exactly one ... must ..."
    EXISTS,          // IF side only

    // Legacy alias names (kept for compatibility)
    ONE_OF,          // alias of EXACTLY_ONE
    ANY_OF,          // alias of AT_LEAST_ONE
    NONE_OF;         // alias of NONE

    /** Should the THEN predicate operator be flipped? (only universal) */
    public boolean requiresFlip() {
        ConstraintCase c = canonical(this);
        return c == ALL_OF;
    }

    /** Wrap a single bound fact line according to the THEN case. */
    public java.util.List<String> applyWrapper(String boundLine) {
        switch (canonical(this)) {
            case NONE, AT_LEAST_ONE:
                return java.util.List.of("not( " + boundLine + " )");
            case EXACTLY_ONE:
                return java.util.List.of(
                        "accumulate(",
                        "  " + boundLine + ",",
                        "  $cnt : count(1)",
                        ") and eval( $cnt != 1 )"
                );
            case ALL_OF, EXISTS:
            default:
                return java.util.List.of(boundLine); // two-line handled outside
        }
    }

    /** Map any alias to its canonical case. */
    public static ConstraintCase canonical(ConstraintCase k) {
        if (k == null) return EXISTS;
        return switch (k) {
            case ONE_OF -> EXACTLY_ONE;
            case ANY_OF -> AT_LEAST_ONE;
            case NONE_OF -> NONE;
            default -> k;
        };
    }

    /** Token mapper tolerant to both canonical and alias spellings from your THEN text or tokens. */
    public static ConstraintCase fromToken(String token) {
        if (token == null) return EXISTS;
        String t = token.trim().toUpperCase();
        return switch (t) {
            case "ALL", "ALL_OF" -> ALL_OF;
            case "AT_LEAST_ONE", "ANY", "ANY_OF" -> AT_LEAST_ONE;
            case "NONE", "NONE_OF", "NO" -> NONE;
            case "EXACTLY_ONE", "ONE_OF", "ONE" -> EXACTLY_ONE;
            case "EXISTS" -> EXISTS;
            default -> EXISTS;
        };
    }

    /** Lightweight detector from raw THEN English (if you use it). */
    public static ConstraintCase fromThenEnglish(String thenEnglish) {
        if (thenEnglish == null) return EXISTS;
        String s = thenEnglish.toLowerCase().trim();
        if (s.startsWith("all ")) return ALL_OF;
        if (s.startsWith("at least one") || s.startsWith("any ")) return AT_LEAST_ONE;
        if (s.startsWith("none")) return NONE;
        if (s.startsWith("exactly one") || s.startsWith("exactly 1") || s.startsWith("one of"))
            return EXACTLY_ONE;
        return EXISTS;
    }
}

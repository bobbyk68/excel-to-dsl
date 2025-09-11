package uk.gov.hmrc.support;

public final class DslMappingBuilder {

    // Builds the RHS DRL for a [when] mapping line from an Atomic hit.
    public static String buildWhenRhs(AtomicHit hit) {
        // From slots: path, op, value, list, quantifier, N...
        String path = hit.slots().getOrDefault("path", "");
        String root = rootType(path);         // e.g., GoodItem
        String leaf = leafField(path);        // e.g., code

        String op   = normalizeOp(hit.slots().get("op"), hit.slots().get("list"));
        String q    = normalizeQuantifier(hit.slots().get("quantifier"));
        String val  = hit.slots().get("value");
        String list = hit.slots().get("list");
        String n    = hit.slots().get("N");   // only for count-based

        String constraint = switch (op) {
            case "equals"     -> leaf + " == {value}";
            case "notEquals"  -> leaf + " != {value}";
            case "in"         -> leaf + " in ({list})";
            case "notIn"      -> "!(" + leaf + " in ({list}))";
            case "gt"         -> leaf + " > {value}";
            case "gte"        -> leaf + " >= {value}";
            case "lt"         -> leaf + " < {value}";
            case "lte"        -> leaf + " <= {value}";
            case "present"    -> "this != null";
            case "absent"     -> "this == null"; // will be wrapped with "not" for NONE
            default           -> "true";
        };

        return switch (q) {
            case "EXISTS"   -> "exists " + root + "( " + constraint + " )";
            case "NONE"     -> "not( " + root + "( " + negateForNone(op, constraint) + " ) )";
            case "ALL"      -> "not( " + root + "( " + counterExample(op, constraint) + " ) )";
            case "AT_LEAST_N","AT_MOST_N","EXACTLY_N" -> accumulate(root, constraint, q, "{N}");
            default         -> root + "( " + constraint + " )";
        };
    }

    // OPTIONAL: a single standard consequence mapping you can reuse for all rules
    public static String buildThenRhs() {
        return "violations.add(new Violation(\"{code}\", Severity.{severity}, \"{message}\", \"{path}\"));";
    }

    // ---------------- helpers (string-level because this is .dsl mapping) ----------------
    private static String rootType(String path) {
        int dot = path.indexOf('.');
        return dot > 0 ? path.substring(0, dot) : path;
    }
    private static String leafField(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
    private static String normalizeOp(String op, String list) {
        if (op == null && list != null) return "in";
        if (op == null) return "equals";
        switch (op.toLowerCase()) {
            case "==", "equals" ->  { return "equals"; }
            case "!=", "not equals" -> { return "notEquals"; }
            case "in", "oneof", "one_of" -> { return "in"; }
            case "not in", "not_one_of" -> { return "notIn"; }
            case ">", "gt", "greater than" -> { return "gt"; }
            case ">=", "gte" -> { return "gte"; }
            case "<", "lt", "less than" -> { return "lt"; }
            case "<=", "lte" -> { return "lte"; }
            case "present", "exists" -> { return "present"; }
            case "absent", "not exists" -> { return "absent"; }
            default -> { return op.toLowerCase(); }
        }
    }
    private static String normalizeQuantifier(String q) {
        if (q == null) return "EXISTS";
        String t = q.toLowerCase();
        if (t.contains("none") || t.startsWith("no ")) return "NONE";
        if (t.contains("all")) return "ALL";
        if (t.contains("at least ") && t.matches(".*\\d+.*")) return "AT_LEAST_N";
        if (t.contains("at most ")  && t.matches(".*\\d+.*")) return "AT_MOST_N";
        if (t.contains("exactly")   && t.matches(".*\\d+.*")) return "EXACTLY_N";
        if (t.contains("at least one") || t.contains("there is at least one")) return "EXISTS";
        return "EXISTS";
    }
    private static String negateForNone(String op, String constraint) {
        // for NONE, we want not( root( <positive-form> ) )
        // if the op already yields a negated constraint (e.g., "absent" produced this==null),
        // we leave it as-is; otherwise just reuse constraint.
        return constraint;
    }
    private static String counterExample(String op, String c) {
        // ALL + IN → counterexample is "notIn"
        // ALL + equals → counterexample is "notEquals", etc.
        return switch (op) {
            case "in"        -> c.replace(" in ", " not in ").replace("!(", "(");
            case "notIn"     -> c.replace("not in", " in ");
            case "equals"    -> c.replace(" == ", " != ");
            case "notEquals" -> c.replace(" != ", " == ");
            default          -> "!(" + c + ")";
        };
    }
    private static String accumulate(String root, String cons, String q, String n) {
        String eval = switch (q) {
            case "AT_LEAST_N" -> "$cnt >= " + n;
            case "AT_MOST_N"  -> "$cnt <= " + n;
            default           -> "$cnt == " + n; // EXACTLY_N
        };
        return "accumulate( $x : " + root + "( " + cons + " ); $cnt : count($x) ) eval( " + eval + " )";
    }
}

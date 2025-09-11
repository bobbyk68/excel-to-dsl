package uk.gov.hmrc.dslgen.support;

import uk.gov.hmrc.rulegen.model.AtomicHit;

import java.util.List;

public final class DslMappingBuilder {

    /**
     * Build the RHS DRL condition for a [when] mapping line, using ordered regex groups.
     * Convention:
     *   groups[0] = path   (e.g., GoodItem.specialProcedures.code)
     *   groups[1] = value  (e.g., GEN3)   -- optional depending on pattern
     *   groups[2] = list   (e.g., G03,G04) -- optional
     *   groups[3] = quantifier (e.g., "at least one") -- optional
     */
    public static String buildWhenRhs(AtomicHit hit) {
        // NEW: pull the literal (raw Excel clause)
        String literal = hit.literal();

// Existing:
        List<String> g = hit.groups();
        String path = (g.size() > 0 && g.get(0) != null) ? g.get(0).trim() : "";

// CHANGE: if path isn’t dotted, recover from literal
        if (path.indexOf('.') < 0 && literal != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("([A-Z][a-zA-Z]+(?:\\.[A-Za-z_][A-Za-z0-9_]*)+)")
                    .matcher(literal);
            if (m.find()) {
                path = m.group(1);
            }
        }

// derive root/leaf (unchanged helpers)
        String root = rootType(path);   // e.g. GoodItem
        String leaf = leafField(path);  // e.g. code

// value/list as you already do
        String value = g.size() > 1 ? g.get(1) : "";
        String list  = g.size() > 2 ? g.get(2) : "";

// operator: first from DSL, fallback to literal
        String op = detectOp(hit.dsl());
        if ("equals".equals(op) && literal != null && !hit.dsl().toLowerCase().contains("equals")) {
            op = detectOp(literal); // fallback heuristic
        }

// quantifier: keep your existing way, fallback to literal text
        String q = g.size() > 3 ? g.get(3) : null;
        if (q == null && literal != null) q = literal; // normalizeQuantifier will parse it

// build constraint (your existing switch)
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
            case "absent"     -> "this == null";
            default           -> "true";
        };

// wrap with quantifier (your existing switch)
        return switch (normalizeQuantifier(q)) {
            case "EXISTS" -> "exists " + root + "( " + constraint + " )";
            case "NONE"   -> "not( "   + root + "( " + constraint + " ) )";
            case "ALL"    -> "not( "   + root + "( " + negateConstraint(op, constraint) + " ) )";
            default       ->             root + "( " + constraint + " )";
        };

    }

    // ---- helpers ----
    private static String rootType(String path) {
        int dot = path.indexOf('.');
        return dot > 0 ? path.substring(0, dot) : path;
    }
    private static String leafField(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private static String normalizeQuantifier(String q) {
        if (q == null) return "EXISTS";
        String t = q.toLowerCase();
        if (t.contains("none")) return "NONE";
        if (t.contains("all")) return "ALL";
        if (t.contains("at least one")) return "EXISTS";
        return "EXISTS";
    }

    private static String detectOp(String dsl) {
        String lower = dsl.toLowerCase();
        if (lower.contains(" not equal")) return "notEquals";
        if (lower.contains(" equals")) return "equals";
        if (lower.contains(" one of")) return "in";
        if (lower.contains(" not one of")) return "notIn";
        if (lower.contains(" greater than or equal")) return "gte";
        if (lower.contains(" greater than")) return "gt";
        if (lower.contains(" less than or equal")) return "lte";
        if (lower.contains(" less than")) return "lt";
        if (lower.contains(" must be present")) return "present";
        if (lower.contains(" must not be present")) return "absent";
        return "equals";
    }

    private static String negateConstraint(String op, String c) {
        return switch (op) {
            case "equals"    -> c.replace("==","!=");
            case "notEquals" -> c.replace("!=","==");
            case "in"        -> c.replace(" in "," not in ");
            case "notIn"     -> c.replace("not in","in");
            default          -> "!("+c+")";
        };
    }
}

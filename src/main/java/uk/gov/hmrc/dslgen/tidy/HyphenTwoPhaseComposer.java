package uk.gov.hmrc.dslgen.tidy;

import uk.gov.hmrc.rules.ast.ConstraintCase;

import java.util.*;
import java.util.regex.Pattern;

public final class HyphenTwoPhaseComposer {
    private static final Pattern PHASE_SPLIT = Pattern.compile("\\s-\\s");
    private final ConstraintComposer composer = new ConstraintComposer();

    public List<String> composeLeaf(ConstraintCase leafCase, String template, String value) {
        String[] parts = PHASE_SPLIT.split(template, 2);

        if (parts.length != 2) {
            // No hyphen → apply leaf constraint as a wrapper if needed,
            // or just bind the line.
            String bound = bind(template, value);
            return applyLeafWrapper(leafCase, bound);
        }

        String left  = parts[0].trim();   // existence/quantifier phrase
        String right = parts[1].trim();   // field/value predicate

        // Phase 1: render the existence/quantifier line (from the leaf's case)
        String base = stripQuantifiers(left);
        if (base.isBlank()) base = "UNKNOWN_PATTERN";
        var spec1 = new ConstraintComposer.ConstraintSpec(base, List.of(), toOperator(leafCase));
        String existenceLine = single(composer.composeDsl(spec1));

        // Phase 2: predicate with bound value
        String predicateLine = bind(right, value);

        return List.of(existenceLine, predicateLine);
    }

    private static List<String> applyLeafWrapper(ConstraintCase kase, String boundLine) {
        switch (kase) {
            case NONE:
                return List.of("not (" + boundLine + ")");
            case EXISTS:
                return List.of("exists (" + boundLine + ")");
            case ONE_OF:
                // Tag for later DRL expansion (accumulate count==1)
                return List.of("/*@oneOf*/ (" + boundLine + ")");
            case NONE_OF:
                // For a single leaf, NONE_OF is equivalent to NONE
                return List.of("not (" + boundLine + ")");
            case ANY_OF:
            case ALL_OF:
            default:
                return List.of(boundLine);
        }
    }

    private static ConstraintComposer.Operator toOperator(ConstraintCase kase) {
        switch (kase) {
            case EXISTS: return ConstraintComposer.Operator.EXISTS;
            case NONE:   return ConstraintComposer.Operator.NONE;
            case ONE_OF: return ConstraintComposer.Operator.ONE_OF;
            default:     return ConstraintComposer.Operator.EXISTS;
        }
    }

    private static String bind(String tpl, String val) {
        return tpl.replace("{value}", val).replace("{1}", val);
    }
    private static String stripQuantifiers(String s) {
        String t = s.replaceAll("(?i)\\b(only one|exactly one|one and only one|no|none|must not exist|must be none|all of|every|there is|exists|present|at least one|must exist)\\b", " ");
        t = t.replaceAll("(?i)\\b(there|must|be|only|one|exactly|and|of|should|to)\\b", " ");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }
    private static String single(List<String> lines) { return lines.isEmpty() ? "" : lines.get(0); }
}

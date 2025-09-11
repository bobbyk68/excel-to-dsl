package uk.gov.hmrc.dslgen.tidy;

import uk.gov.hmrc.rules.ast.ConstraintCase;

public final class ConstraintCaseResolver {

    public ConstraintCase fromToken(String token, ConstraintCase defaultCase) {
        if (token == null || token.isBlank()) return defaultCase;
        String t = token.trim().toUpperCase();
        switch (t) {
            case "EXISTS": case "PRESENT": case "AT_LEAST_ONE": return ConstraintCase.EXISTS;
            case "NONE":   case "NOT":     case "MUST_NOT":      return ConstraintCase.NONE;
            case "ANY":    case "ANY_OF":                        return ConstraintCase.ANY_OF;
            case "ALL":    case "ALL_OF":                        return ConstraintCase.ALL_OF;
            case "ONE":    case "ONE_OF": case "ONLY_ONE":       return ConstraintCase.ONE_OF;
            case "NONE_OF":                                   return ConstraintCase.NONE_OF;
            default: return defaultCase;
        }
    }
}

package com.example.dslgen;

import java.util.*;

public class DslBuilder {

    public static class ParsedDsl {
        private final String lhs;
        private final String rhs;

        public ParsedDsl(String lhs, String rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        public String getLhs() {
            return lhs;
        }

        public String getRhs() {
            return rhs;
        }
    }

    public static class Result {
        public final List<String> dslLines = new ArrayList<>();
        public final List<String> dslrLines = new ArrayList<>();
    }

    public Result build(List<RuleRow> rules) {
        Result result = new Result();
        Set<String> seenConditions = new HashSet<>();

        for (RuleRow row : rules) {
            List<String> conditionKeys = new ArrayList<>();

            for (String cond : row.getConditions()) {
                boolean matched = false;
                for (WhenPatternRule rule : WhenPatternRule.values()) {
                    ParsedDsl parsed = rule.tryMatch(cond);
                    if (parsed != null) {
                        if (seenConditions.add(parsed.getLhs())) {
                            result.dslLines.add("[when] " + parsed.getLhs() + " = " + parsed.getRhs());
                        }
                        conditionKeys.add(parsed.getLhs());
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    System.out.println("⚠️ Unmatched WHEN pattern: " + cond);
                }
            }

            result.dslrLines.add("rule \"" + row.getName() + "\"");
            result.dslrLines.add("when");
            result.dslrLines.add("    the ProcedureCategory is " + row.getProcedureCategory());
            result.dslrLines.add("    and the DeclarationType is " + row.getDeclarationType());
            for (String key : conditionKeys) {
                result.dslrLines.add("    and " + key);
            }
            result.dslrLines.add("then");
            result.dslrLines.add("    System.out.println(\"ERROR: " + row.getErrorCode() + "\");");
            result.dslrLines.add("end\n");
        }

        return result;
    }
}
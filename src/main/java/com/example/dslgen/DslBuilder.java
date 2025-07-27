package com.example.dslgen;

import com.example.dslgen.pattern.WhenPatternRule;
import com.example.dslgen.pattern.ThenPatternRule;

import java.util.*;

public class DslBuilder {

    private static final List<String> ALL_DECL_TYPES = Arrays.asList("A", "D", "Y", "Z", "C", "J", "F");

    public static class Result {
        public final List<String> dslLines;
        public final List<String> dslrLines;

        public Result(List<String> dslLines, List<String> dslrLines) {
            this.dslLines = dslLines;
            this.dslrLines = dslrLines;
        }
    }

    public Result build(List<RuleRow> rules) {
        List<String> dslLines = new ArrayList<>();
        List<String> dslrLines = new ArrayList<>();
        Set<String> dslLhsTracker = new HashSet<>();

        for (RuleRow row : rules) {
            String ruleName = row.getName();
            String procCat = row.getProcedureCategory();
            String declType = row.getDeclarationType();
            String conditionText = row.getCondition();
            String errorCode = row.getErrorCode();

            String whenLhs = null, whenRhs = null;
            for (WhenPatternRule pattern : WhenPatternRule.values()) {
                if (pattern.matches(conditionText)) {
                    whenLhs = pattern.getLhs(conditionText);
                    whenRhs = pattern.getRhs(conditionText);
                    break;
                }
            }

            if (whenLhs == null) {
                System.out.println("⚠️ Unmatched WHEN pattern: " + conditionText);
                whenLhs = "unparsed when condition";
                whenRhs = "// TODO";
            }

            String thenLhs = null, thenRhs = null;
            for (ThenPatternRule pattern : ThenPatternRule.values()) {
                if (pattern.matches(conditionText)) {
                    thenLhs = pattern.getLhs(conditionText);
                    thenRhs = pattern.getRhs(conditionText);
                    break;
                }
            }

            if (thenLhs == null) {
                thenLhs = "raise error";
                thenRhs = "System.out.println(\"ERROR: " + errorCode + "\");";
            }

            // DSL lines
            if (!dslLhsTracker.contains(whenLhs)) {
                dslLines.add("[when] " + whenLhs + " = " + whenRhs);
                dslLhsTracker.add(whenLhs);
            }

            if (!dslLhsTracker.contains(thenLhs)) {
                dslLines.add("[then] " + thenLhs + " = " + thenRhs);
                dslLhsTracker.add(thenLhs);
            }

            // Category
            String catLhs = "the ProcedureCategory is " + procCat;
            String catRhs = "declaration.getProcedureCategory().equals(\"" + procCat + "\")";
            if (!dslLhsTracker.contains(catLhs)) {
                dslLines.add("[when] " + catLhs + " = " + catRhs);
                dslLhsTracker.add(catLhs);
            }

            // Declaration type(s)
            if ("ALL".equalsIgnoreCase(declType)) {
                for (String dt : ALL_DECL_TYPES) {
                    String dtLhs = "the DeclarationType is " + dt;
                    String dtRhs = "declaration.getType().equals(\"" + dt + "\")";
                    if (!dslLhsTracker.contains(dtLhs)) {
                        dslLines.add("[when] " + dtLhs + " = " + dtRhs);
                        dslLhsTracker.add(dtLhs);
                    }
                }
            } else {
                for (String dt : declType.split(",")) {
                    String dtTrim = dt.trim();
                    String dtLhs = "the DeclarationType is " + dtTrim;
                    String dtRhs = "declaration.getType().equals(\"" + dtTrim + "\")";
                    if (!dslLhsTracker.contains(dtLhs)) {
                        dslLines.add("[when] " + dtLhs + " = " + dtRhs);
                        dslLhsTracker.add(dtLhs);
                    }
                }
            }

            // DSLR Rule
            StringBuilder rule = new StringBuilder();
            rule.append("rule \"").append(ruleName).append("\"\n");
            rule.append("when\n");
            rule.append("    the ProcedureCategory is ").append(procCat).append("\n");

            if ("ALL".equalsIgnoreCase(declType)) {
                for (String dt : ALL_DECL_TYPES) {
                    rule.append("    or the DeclarationType is ").append(dt).append("\n");
                }
            } else {
                for (String dt : declType.split(",")) {
                    rule.append("    or the DeclarationType is ").append(dt.trim()).append("\n");
                }
            }

            rule.append("    ").append(whenLhs).append("\n");
            rule.append("then\n");
            rule.append("    ").append(thenLhs).append("\n");
            rule.append("    System.out.println(\"ERROR: ").append(errorCode).append("\");\n");
            rule.append("end\n");

            dslrLines.add(rule.toString());
        }

        return new Result(dslLines, dslrLines);
    }
}

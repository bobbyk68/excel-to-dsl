package com.example.dslgen.builder;

import com.example.dslgen.RuleRow;
import com.example.dslgen.matcher.PatternMatcher;

import java.util.*;

public class DslBuilder {

    public record ParsedDsl(String lhs, String rhs) {}

    public static class Result {
        public final List<String> dslLines = new ArrayList<>();
        public final List<String> dslrLines = new ArrayList<>();
    }

    private final PatternMatcher whenMatcher;
    private final PatternMatcher thenMatcher;

    public DslBuilder(PatternMatcher whenMatcher, PatternMatcher thenMatcher) {
        this.whenMatcher = whenMatcher;
        this.thenMatcher = thenMatcher;
    }

    public Result build(List<RuleRow> rules) {
        Result result = new Result();
        Map<String, String> lhsToRhsMap = new LinkedHashMap<>(); // Preserve order and avoid duplicates


        for (RuleRow row : rules) {
            List<String> conditionKeys = new ArrayList<>();

            for (String cond : row.getConditions()) {
                ParsedDsl parsed = whenMatcher.tryMatch(cond);

                if (parsed != null) {
                    lhsToRhsMap.putIfAbsent(parsed.lhs, parsed.rhs);
                    conditionKeys.add("[when] " + parsed.lhs() + " = " + parsed.rhs());
                } else {
                    System.out.println("⚠️ Unmatched WHEN: " + cond);
                }
            }

            // Handle THEN
            List<String> thenKeys = new ArrayList<>();
            String fallbackAction = "System.out.println(\"ERROR: " + row.getErrorCode() + "\");";
            String actionText = "ERROR: " + row.getErrorCode();


            ParsedDsl thenParsed = thenMatcher.tryMatch(actionText);

            if (thenParsed != null) {
                result.dslLines.add("[then] " + thenParsed.lhs + " = " + thenParsed.rhs);
                result.dslrLines.add("    " + thenParsed.lhs);
            } else {
                result.dslrLines.add("    System.out.println(\"ERROR: " + row.getErrorCode() + "\");");
            }

            // Build DSLR Rule
            result.dslrLines.add("rule \"" + row.getName() + "\"");
            result.dslrLines.add("when");
            result.dslrLines.add("    the ProcedureCategory is " + row.getProcedureCategory());
            result.dslrLines.add("    and the DeclarationType is " + row.getDeclarationType());
            for (String k : conditionKeys) {
                result.dslrLines.add("    and " + k);
            }
            result.dslrLines.add("then");
            for (String k : thenKeys) {
                result.dslrLines.add("    " + k);
            }
            result.dslrLines.add("end\n");
        }

        return result;
    }
}

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
        Set<String> seenWhens = new HashSet<>();
        Set<String> seenThens = new HashSet<>();

        for (RuleRow row : rules) {
            List<String> whenKeys = new ArrayList<>();
            for (String cond : row.getConditions()) {
                ParsedDsl parsed = whenMatcher.tryMatch(cond);
                if (parsed != null) {
                    if (seenWhens.add(parsed.lhs())) {
                        result.dslLines.add("[when] " + parsed.lhs() + " = " + parsed.rhs());
                    }
                    whenKeys.add(parsed.lhs());
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
                if (seenThens.add(thenParsed.lhs())) {
                    result.dslLines.add("[then] " + thenParsed.lhs() + " = " + thenParsed.rhs());
                }
                thenKeys.add(thenParsed.lhs());
            } else {
                thenKeys.add(fallbackAction);
            }

            // Build DSLR Rule
            result.dslrLines.add("rule \"" + row.getName() + "\"");
            result.dslrLines.add("when");
            result.dslrLines.add("    the ProcedureCategory is " + row.getProcedureCategory());
            result.dslrLines.add("    and the DeclarationType is " + row.getDeclarationType());
            for (String k : whenKeys) {
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

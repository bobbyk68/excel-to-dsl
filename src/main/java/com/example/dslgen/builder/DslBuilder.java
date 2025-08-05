package com.example.dslgen.builder;

import com.example.dslgen.RuleRow;
import com.example.dslgen.matcher.PatternMatcher;

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

    private final PatternMatcher whenMatcher;
    private final PatternMatcher thenMatcher;

    public DslBuilder(PatternMatcher whenMatcher, PatternMatcher thenMatcher) {
        this.whenMatcher = whenMatcher;
        this.thenMatcher = thenMatcher;
    }

    public Result build(List<RuleRow> rules) {
        Result result = new Result();
        Set<String> seenConditions = new HashSet<>();

        for (RuleRow row : rules) {
            List<String> conditionKeys = new ArrayList<>();

            for (String cond : row.getConditions()) {
                ParsedDsl parsed = whenMatcher.tryMatch(cond);
                if (parsed != null) {
                    if (seenConditions.add(parsed.getLhs())) {
                        result.dslLines.add("[when] " + parsed.getLhs() + " = " + parsed.getRhs());
                    }
                    conditionKeys.add(parsed.getLhs());
                } else {
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

            ParsedDsl thenParsed = thenMatcher.tryMatch(row.getErrorMessage());
            if (thenParsed != null) {
                if (seenConditions.add(thenParsed.getLhs())) {
                    result.dslLines.add("[then] " + thenParsed.getLhs() + " = " + thenParsed.getRhs());
                }
                result.dslrLines.add("    " + thenParsed.getLhs());
            } else {
                result.dslrLines.add("    System.out.println(\"ERROR: " + row.getErrorCode() + "\");");
            }

            result.dslrLines.add("end\n");
        }

        return result;
    }
}

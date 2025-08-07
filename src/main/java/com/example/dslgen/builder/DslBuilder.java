package com.example.dslgen.builder;

import com.example.dslgen.RuleRow;
import com.example.dslgen.pattern.PatternMatcher;

import java.util.*;

public class DslBuilder {

    public record ParsedDsl(String rawLhs, String renderedLhs, String rawRhs, String renderedRhs) {}

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
        Set<String> seenDslConditions = new HashSet<>();

        for (RuleRow row : rules) {
            List<String> conditionKeys = new ArrayList<>();

            for (String cond : row.getConditions()) {
                ParsedDsl parsed = whenMatcher.tryMatch(cond);
                if (parsed != null) {
                    // ✅ Only add to DSL file once
                    if (seenDslConditions.add(parsed.rawLhs())) {
                        result.dslLines.add("[when] " + parsed.rawLhs() + " = \"" + parsed.rawRhs() + "\"");
                    }
                    // ✅ Always collect for DSLR
                    conditionKeys.add(parsed.renderedLhs());
                } else {
                    System.out.println("⚠️ Unmatched WHEN condition: " + cond);
                }
            }

            // ✅ Then clause
            ParsedDsl thenParsed = thenMatcher != null ? thenMatcher.tryMatch(row.getAction()) : null;
            if (thenParsed != null && seenDslConditions.add(thenParsed.rawLhs())) {
                result.dslLines.add("[then] " + thenParsed.rawLhs() + " = " + thenParsed.rawRhs());
            }

            // ✅ DSLR Generation
            result.dslrLines.add("rule \"" + row.getName() + "\"");
            result.dslrLines.add("when");
            result.dslrLines.add("    the ProcedureCategory is " + row.getProcedureCategory());
            result.dslrLines.add("    and the DeclarationType is " + row.getDeclarationType());
            for (String key : conditionKeys) {
                result.dslrLines.add("    and " + key);
            }
            result.dslrLines.add("then");

            if (thenParsed != null) {
                result.dslrLines.add("    " + thenParsed.renderedRhs() + ";");
            } else {
                result.dslrLines.add("    System.out.println(\"ERROR: " + row.getErrorCode() + "\");");
            }

            result.dslrLines.add("end\n");
        }

        return result;
    }
}

package com.example.dslgen.pattern.loader;

import com.example.dslgen.builder.DslBuilder;
import com.example.dslgen.pattern.PatternRule;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternDefinition implements PatternRule {

    private final List<String> patterns; // List of regex patterns
    private final String lhs;            // DSL LHS key (e.g., "goods item has X")
    private final String rhs;            // DSL RHS expression (e.g., "checkX({val})")

    public PatternDefinition(List<String> patterns, String lhs, String rhs) {
        this.patterns = patterns;
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public String getLhs() {
        return lhs;
    }

    public String getRhs() {
        return rhs;
    }

    @Override
    public DslBuilder.ParsedDsl tryMatch(String line) {
        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(line);

            if (matcher.matches()) {
                String resolvedRhs = rhs;
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    resolvedRhs = resolvedRhs.replace("{val" + i + "}", matcher.group(i));
                }
                // Also allow {val} as alias for first group
                resolvedRhs = resolvedRhs.replace("{val}", matcher.group(1));

                return new DslBuilder.ParsedDsl(lhs, resolvedRhs);
            }
        }
        return null;
    }
}

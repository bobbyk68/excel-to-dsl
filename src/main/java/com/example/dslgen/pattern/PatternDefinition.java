package com.example.dslgen.pattern.loader;

import com.example.dslgen.builder.DslBuilder;
import com.example.dslgen.pattern.PatternRule;

import java.util.List;

public class PatternDefinition {
    private final List<String> patterns; // e.g. regex variations
    private final String lhs;
    private final String rhs;

    public PatternDefinition(List<String> patterns, String lhs, String rhs) {
        this.patterns = patterns;
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public DslBuilder.ParsedDsl tryMatch(String line) {
        for (String pattern : patterns) {
            if (line.matches(pattern)) {
                String rhsResolved = line.replaceAll(pattern, rhs); // or Matcher if you have groups
                return new DslBuilder.ParsedDsl(lhs, rhsResolved);
            }
        }
        return null;
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
}

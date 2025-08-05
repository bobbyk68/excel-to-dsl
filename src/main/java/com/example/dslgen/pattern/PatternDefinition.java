package com.example.dslgen.pattern;

import java.util.List;

public class PatternDefinition {
    private final List<String> patterns;
    private final String lhs;
    private final String rhs;

    public PatternDefinition(List<String> patterns, String lhs, String rhs) {
        this.patterns = patterns;
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public List<String> getPatterns() { return patterns; }
    public String getLhs() { return lhs; }
    public String getRhs() { return rhs; }
}

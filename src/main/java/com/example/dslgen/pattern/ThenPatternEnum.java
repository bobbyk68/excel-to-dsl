package com.example.dslgen.pattern;

import java.util.List;

public enum ThenPatternEnum implements com.example.dslgen.pattern.PatternDefinitionProvider {
    ERROR_PRINT("print error (E\\d{4})", "print error {code}", "System.out.println(\"ERROR: {code}\");");

    public String getPattern() {
        return pattern;
    }

    private final String pattern;
    private final String lhs;
    private final String rhs;

    ThenPatternEnum(String pattern, String lhs, String rhs) {
        this.pattern = pattern;
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public List<PatternDefinition> getPatternDefinitions() {
        return List.of(new PatternDefinition(List.of(pattern), lhs, rhs));
    }
}

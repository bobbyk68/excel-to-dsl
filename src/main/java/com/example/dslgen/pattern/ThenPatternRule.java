package com.example.dslgen.pattern;

import java.util.regex.*;

public enum ThenPatternRule {
    DEFAULT_THEN(
        "raise an error",
        "default action",
        "// System.out.println"
    );

    private final Pattern pattern;
    private final String dslLhs;
    private final String javaCode;

    ThenPatternRule(String regex, String dslLhs, String javaCode) {
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.dslLhs = dslLhs;
        this.javaCode = javaCode;
    }

    public boolean matches(String input) {
        return pattern.matcher(input).find();
    }

    public String getLhs(String input) {
        return dslLhs;
    }

    public String getRhs(String input) {
        return javaCode;
    }
}

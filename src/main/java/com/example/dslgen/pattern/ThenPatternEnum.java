// ThenPatternEnum.java
package com.example.dslgen.pattern;

import com.example.dslgen.builder.DslBuilder;

import java.util.List;

public enum ThenPatternEnum implements PatternRule {

    PRINT_ERROR(List.of("print error code (E\\d{4})"),
            "print error code {val}",
            "System.out.println(\"ERROR: {val}\");")
    ;

    private final List<String> regex;
    private final String lhs;
    private final String rhs;

    ThenPatternEnum(List<String> regex, String lhs, String rhs) {
        this.regex = regex;
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public DslBuilder.ParsedDsl tryMatch(String line) {
        for (String pattern : regex) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(line);
            if (matcher.matches()) {
                String val = matcher.group(1);
                return new DslBuilder.ParsedDsl(lhs.replace("{val}", val), rhs.replace("{val}", val));
            }
        }
        return null;
    }

    public String getLhs() {
        return lhs;
    }

    public String getRhs() {
        return rhs;
    }

    public List<String> getRegex() {
        return regex;
    }
}

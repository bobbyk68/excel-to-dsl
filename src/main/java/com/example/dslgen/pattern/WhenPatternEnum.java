package com.example.dslgen.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.dslgen.builder.DslBuilder.ParsedDsl;

public enum WhenPatternEnum implements PatternRule {
    U_MISSING(
        new String[]{"if (U\\d{3}) is not present"},
        "when {val} is not present",
        "hasField(\"{val}\") == false"
    ),
    U_INCORRECT(
        new String[]{"if (U\\d{3}) is incorrect"},
        "when {val} is incorrect",
        "isFieldIncorrect(\"{val}\")"
    ),
    FIELD_DUPLICATED(
        new String[]{"if (\\w+) appears more than once"},
        "when {val} appears more than once",
        "isDuplicated(\"{val}\")"
    );

    private final Pattern[] patterns;
    private final String lhs;
    private final String rhs;

    WhenPatternEnum(String[] regexes, String lhs, String rhs) {
        this.patterns = new Pattern[regexes.length];
        for (int i = 0; i < regexes.length; i++) {
            this.patterns[i] = Pattern.compile(regexes[i], Pattern.CASE_INSENSITIVE);
        }
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public ParsedDsl tryMatch(String line) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String val = matcher.group(1);
                return new ParsedDsl(
                    lhs.replace("{val}", val),
                    rhs.replace("{val}", val)
                );
            }
        }
        return null;
    }

    public Pattern[] getPatterns() {
        return patterns;
    }

    public String getLhs() {
        return lhs;
    }

    public String getRhs() {
        return rhs;
    }

    public static WhenPatternEnum[] valuesArray() {
        return values();
    }
}

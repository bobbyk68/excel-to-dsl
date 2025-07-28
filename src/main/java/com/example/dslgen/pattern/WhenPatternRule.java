package com.example.dslgen.pattern;

import java.util.regex.*;

public enum WhenPatternRule {
    NOT_PRESENT("if (.+) is not present", "{0} is not present", "!declaration.hasField(\"{0}\")"),
    IS_INCORRECT("if (.+) is incorrect", "{0} is incorrect", "!declaration.isCorrect(\"{0}\")"),
    DUPLICATED("if (.+) is duplicated", "{0} is duplicated", "declaration.isDuplicated(\"{0}\")"),
    APPEARS_MORE_THAN_ONCE("if (.+) appears more than once", "{0} appears more than once", "declaration.appearsMoreThanOnce(\"{0}\")"),
    MUST_NOT_APPEAR("if (.+) must not appear", "{0} must not appear", "!declaration.contains(\"{0}\")"),
    MUST_BE_EXACTLY_TWO("if (.+) must be exactly two", "{0} must be exactly two", "declaration.count(\"{0}\") == 2"),
    BOTH_MISSING("if (.+) and (.+) are both missing", "{0} and {1} are both missing", "!declaration.hasField(\"{0}\") && !declaration.hasField(\"{1}\")"),
    NONE_OF_MUST_APPEAR("none of (.+) must appear", "none of {0} must appear", "!declaration.anyPresent({0})");

    private final Pattern pattern;
    private final String dslKey;
    private final String javaTemplate;

    WhenPatternRule(String regex, String dslKey, String javaTemplate) {
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.dslKey = dslKey;
        this.javaTemplate = javaTemplate;
    }

    public DslBuilder.ParsedDsl tryMatch(String input) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.matches()) {
            String[] args = new String[matcher.groupCount()];
            for (int i = 0; i < matcher.groupCount(); i++) args[i] = matcher.group(i + 1);
            String key = dslKey;
            String value = javaTemplate;
            for (int i = 0; i < args.length; i++) {
                key = key.replace("{" + i + "}", args[i]);
                value = value.replace("{" + i + "}", args[i]);
            }
            return new DslBuilder.ParsedDsl(key, value);
        }
        return null;
    }
}
package com.example.dslgen.pattern;

import com.example.dslgen.builder.DslBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final String dslKeyTemplate;
    private final String rhsTemplate;
    private String resolvedLhs;
    private String resolvedRhs;

    WhenPatternRule(String regex, String dslKeyTemplate, String rhsTemplate) {
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.dslKeyTemplate = dslKeyTemplate;
        this.rhsTemplate = rhsTemplate;
    }

    public boolean matches(String input) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.matches()) return false;

        resolvedLhs = dslKeyTemplate;
        resolvedRhs = rhsTemplate;
        for (int i = 0; i < matcher.groupCount(); i++) {
            String val = matcher.group(i + 1);
            resolvedLhs = resolvedLhs.replace("{" + i + "}", val);
            resolvedRhs = resolvedRhs.replace("{" + i + "}", val);
        }
        return true;
    }

    public String getLhs() {
        return resolvedLhs;
    }

    public String getRhs() {
        return resolvedRhs;
    }

    public DslBuilder.ParsedDsl tryMatch(String input) {
        if (matches(input)) {
            return new DslBuilder.ParsedDsl(resolvedLhs, resolvedRhs);
        }
        return null;
    }
}

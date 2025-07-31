package com.example.dslgen;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum Whens {
    MISSING_FIELD(
        new String[]{
            "(?i)if (\\w+) is missing",
            "(?i)if (\\w+) is not present"
        },
        "if {val} is missing",
        "!hasField(\"{val}\")"
    ),

    DUPLICATE_FIELD(
        new String[]{
            "(?i)if (\\w+) is duplicated",
            "(?i)if (\\w+) appears more than once"
        },
        "if {val} is duplicated",
        "isDuplicated(\"{val}\")"
    ),

    FIELD_INCORRECT(
        new String[]{
            "(?i)if (\\w+) is incorrect"
        },
        "if {val} is incorrect",
        "!isCorrect(\"{val}\")"
    ),

    ADDITIONAL_DOC_PRESENT(
        new String[]{
            "(?i)if goodsItem\\.additionalDocuments\\.type\\.code is present"
        },
        "if goodsItem.additionalDocuments.type.code is present",
        "hasField(\"goodsItem.additionalDocuments.type.code\")"
    );

    private final Pattern[] patterns;
    private final String lhsTemplate;
    private final String rhsTemplate;

    Whens(String[] regexes, String lhsTemplate, String rhsTemplate) {
        this.patterns = new Pattern[regexes.length];
        for (int i = 0; i < regexes.length; i++) {
            this.patterns[i] = Pattern.compile(regexes[i]);
        }
        this.lhsTemplate = lhsTemplate;
        this.rhsTemplate = rhsTemplate;
    }

    public DslBuilder.ParsedDsl tryMatch(String line) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String value = matcher.group(1);
                String lhs = lhsTemplate.replace("{val}", value);
                String rhs = rhsTemplate.replace("{val}", value);
                return new DslBuilder.ParsedDsl(lhs, rhs);
            }
        }
        return null;
    }
}
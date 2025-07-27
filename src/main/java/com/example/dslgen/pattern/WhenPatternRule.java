package com.example.dslgen.pattern;

import java.util.regex.*;

public enum WhenPatternRule {
    APPEARS_MORE_THAN_ONCE(
        "if (.+?) appears more than once",
        "there is more than one occurrence of {code}",
        "goodItem.getAdditionalDocuments().stream().filter(doc -> doc.getType().getCode().equals(\"{code}\")).count() > 1"
    ),
    IS_NOT_PRESENT(
        "if (.+?) is not present",
        "there is no occurrence of {code}",
        "goodItem.getAdditionalDocuments().stream().noneMatch(doc -> doc.getType().getCode().equals(\"{code}\"))"
    );

    private final Pattern pattern;
    private final String dslLhsTemplate;
    private final String javaConditionTemplate;

    WhenPatternRule(String regex, String dslLhsTemplate, String javaConditionTemplate) {
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.dslLhsTemplate = dslLhsTemplate;
        this.javaConditionTemplate = javaConditionTemplate;
    }

    public boolean matches(String input) {
        return pattern.matcher(input).find();
    }

    public String getLhs(String input) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            String code = matcher.group(1).trim();
            return dslLhsTemplate.replace("{code}", code);
        }
        return "unparsed when";
    }

    public String getRhs(String input) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            String code = matcher.group(1).trim();
            return javaConditionTemplate.replace("{code}", code);
        }
        return "// TODO";
    }
}

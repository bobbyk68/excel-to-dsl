package uk.gov.hmrc.rules;

import java.util.Map;

public record RuleRow(
        String ruleName,
        String lhsKey,
        String whenTemplateId,
        String thenTemplateId,
        Map<String, Object> bindings
) {}
// v2
package uk.gov.hmrc.rules.build;

import java.util.Map;
// v4
package uk.gov.hmrc.rules.build;

import java.util.List;
import java.util.Map;

/**
 * Shape:
 * - ruleName   : String
 * - procCats   : String (raw, e.g. "AUX, CORE")
 * - decTypes   : String (raw, e.g. "A,D" or "ALL")
 * - conditions : List<String>
 * - errorCode  : String
 * - original   : String
 * - whenTemplateId / thenTemplateId : String
 * - bindings   : Map<String,Object> (optional extra placeholders)
 */
public record RuleRow(
        String ruleName,
        String procCats,
        String decTypes,
        List<String> conditions,
        String errorCode,
        String original,
        String whenTemplateId,
        String thenTemplateId,
        Map<String, Object> bindings
) {}
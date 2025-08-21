// v2
package uk.gov.hmrc.rules.build;

import java.util.Map;

/**
 * Excel-backed rule row.
 * - conditions: the LHS statements from Excel (can be the full combined condition text)
 * - errorCode : the error code from Excel (e.g., DMS12345)
 * - original  : the original Excel cell text that contained all statements (for traceability)
 *
 * Back-compat:
 * - lhsKey is optional. If null/blank, the builder derives it from `conditions`.
 */
public record RuleRow(
        String ruleName,

        // Optional explicit LHS line/handle to check in main.dsl
        String lhsKey,

        // NEW: full conditions text from Excel (LHS source)
        String conditions,

        // NEW: error code from Excel (becomes a default binding "code" if not provided)
        String errorCode,

        // NEW: original Excel column text that contained all statements (raw trace)
        String original,

        // Template IDs (still used by the refactored builder)
        String whenTemplateId,
        String thenTemplateId,

        // Bindings for ${...} placeholders
        Map<String, Object> bindings
) {}
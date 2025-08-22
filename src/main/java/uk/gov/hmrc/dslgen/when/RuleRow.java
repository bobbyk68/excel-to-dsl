package uk.gov.hmrc.dslgen.when;

import java.util.List;

/**
 * Contract for a single rule row.
 * Populated by ExcelReader.
 */
public interface RuleRow {
    String ruleName();
    List<String> declarationTypes();
    List<String> procedureCategories();
    String param();
    String errorMessage();
    List<String> whenCandidates();
}
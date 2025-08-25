package uk.gov.hmrc.dslgen.dslgen;

import java.util.List;

/** Contract for a single input rule row (produced by ExcelReader or other loaders). */
public interface RuleRow {
    String ruleName();
    List<String> declarationTypes();
    List<String> procedureCategories();
    String param();
    String errorMessage();
    List<String> whenCandidates();
}

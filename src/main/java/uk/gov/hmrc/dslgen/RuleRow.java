package uk.gov.hmrc.dslgen;

import java.util.List;

/** Contract for a single input rule row (produced by ExcelReader or other loaders). */
public interface RuleRow {
    String ruleName();

    // Structured fields you may hydrate into DSL if needed
    List<String> declarationTypes();
    List<String> procedureCategories();
    String param();

    // Text that will be used for RHS Java in the DSLR (not DSL)
    String errorMessage();

    // All LHS English phrases from Excel (both IF and former THEN-as-condition)
    List<String> whenCandidates();
}

package uk.gov.hmrc.dslgen;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock ExcelReader for now.
 * Replace with Apache POI to load real XLSX.
 */
public class ExcelReader {

    public List<RuleRow> read(String path) {
        List<RuleRow> rows = new ArrayList<>();

        // Hardcoded example row
        rows.add(new RuleRow() {
            @Override
            public String ruleName() { return "BR236_1791_InvalidAuthType"; }

            @Override
            public List<String> declarationTypes() { return List.of("A", "D"); }

            @Override
            public List<String> procedureCategories() { return List.of("Cat1"); }

            @Override
            public String param() { return "123"; }

            @Override
            public String errorMessage() { return "Invalid declaration: unsupported auth type"; }

            @Override
            public List<String> whenCandidates() {
                return List.of(
                        "Declaration type oneof A,D",
                        "AuthorizationHolder.authorizationType.code must equal 123"
                );
            }
        });

        return rows;
    }
}
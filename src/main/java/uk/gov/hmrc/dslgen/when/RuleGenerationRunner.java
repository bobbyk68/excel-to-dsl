package uk.gov.hmrc.dslgen.when;

import java.nio.file.*;
import java.util.*;

public class RuleGenerationRunner {

    public static void main(String[] args) throws Exception {
        // 1. Paths to template files
        Path whenTemplate = Paths.get("src/main/resources/config/when-template.json");
        Path thenTemplate = Paths.get("src/main/resources/config/then-template.json");

        // 2. Pattern matchers (your own implementations)
        PatternMatcher whenMatcher = new WhenPatternMatcher();
        PatternMatcher thenMatcher = new ThenPatternMatcher();

        // 3. Create builder
        DslBuilder builder = new DslBuilder(whenMatcher, thenMatcher, whenTemplate, thenTemplate);

        // 4. Load your Excel file → List<RuleRow>
        List<DslBuilder.RuleRow> rows = ExcelLoader.load("rules.xlsx");

        // 5. Build all rules into DSLR
        DslBuilder.ResultAll result = builder.build(rows);

        // 6. Inspect result (optional)
        result.getRules().forEach((ruleName, sections) -> {
            System.out.println("Rule: " + ruleName);
            System.out.println("WHEN: " + sections.get("when"));
            System.out.println("THEN: " + sections.get("then"));
        });
    }
}
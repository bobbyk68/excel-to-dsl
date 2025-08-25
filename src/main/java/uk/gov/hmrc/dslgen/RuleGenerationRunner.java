package uk.gov.hmrc.dslgen;

import uk.gov.hmrc.dslgen.when.WhenPatternMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Coordinates reading Excel → building DSL rules → writing a .dslr file.
 */
public class RuleGenerationRunner {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java -jar dslgen.jar <input.xlsx> <output.dslr>");
            System.exit(1);
        }

        String excelPath = args[0];
        Path outputPath = Path.of(args[1]);

        // 1. Read Excel into RuleRow objects
        ExcelReader reader = new ExcelReader(excelPath);
        List<DslBuilder.RuleRow> rows = reader.rows();

        // 2. Instantiate matchers + builder
        WhenPatternMatcher whenMatcher = new WhenPatternMatcher();
        ThenPatternMatcher thenMatcher = new ThenPatternMatcher();

        DslBuilder builder = new DslBuilder(
                whenMatcher,
                thenMatcher,
                Path.of("src/main/resources/when-template.json"), // reserved for later
                Path.of("src/main/resources/then-templates.json")  // reserved for later
        );

        // 3. Build DSL lines
        List<String> dslrLines = builder.build(rows);

        // 4. Write .dslr file
        Files.write(outputPath, dslrLines);

        System.out.println("✅ Generated DSLR: " + outputPath.toAbsolutePath());
    }
}

package uk.gov.hmrc.dslgen;

import java.nio.file.Path;
import java.util.List;

public class RuleGenerationRunner {

    public static void main(String[] args) {
        String excelPath = args.length > 0 ? args[0] : "rules.xlsx";
        Path out = Path.of("target/generated-rules.dslr");

        ExcelReader reader = new ExcelReader();
        List<RuleRow> rows = reader.read(excelPath);

        new DslBuilder().build(rows, out);
        System.out.println("Generated: " + out.toAbsolutePath());
    }
}
package uk.gov.hmrc.dslgen.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import uk.gov.hmrc.dslgen.io.ExcelLoader;
import uk.gov.hmrc.dslgen.model.RuleBook;
import uk.gov.hmrc.dslgen.service.DedupService;
import uk.gov.hmrc.dslgen.io.JsonWriter;

import java.nio.file.Path;

@Component
public class JsonNormaliserRunner implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        if (args.length > 0 && args[0].equals("normalise")) {
            var rows = ExcelLoader.readIfThenRows(
                Path.of("rules.xlsx"),
                "Sheet1",
                null,
                0,
                "IF_Block",
                "THEN_Block");

            RuleBook book = DedupService.buildRuleBook(rows);
            JsonWriter.write(book, Path.of("rules-normalised.json"));
            System.out.println("✅ Wrote rules-normalised.json");
            System.exit(0);
        }
    }
}

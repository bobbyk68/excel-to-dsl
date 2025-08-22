package uk.gov.hmrc.dslgen.then;

import uk.gov.hmrc.dslgen.RuleRow;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class ThenPatternMatcher {

    public List<String> collectAll(RuleRow row) {
        List<String> results = new ArrayList<>();

        if (row.errorMessage() != null && !row.errorMessage().isBlank()) {
            results.add("System.out.println(\"" + row.errorMessage() + "\");");
        } else {
            writeCatchall("[then]" + "Unknown RHS = Unknown RHS");
            results.add("// Unmapped RHS");
        }

        return results;
    }

    private void writeCatchall(String dslLine) {
        try {
            Path path = Path.of("target/catchall.dsl");
            Files.writeString(path, dslLine + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write catchall.dsl", e);
        }
    }
}
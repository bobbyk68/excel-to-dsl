package uk.gov.hmrc.dslgen;

import uk.gov.hmrc.dslgen.when.WhenPatternMatcher;
import uk.gov.hmrc.dslgen.then.ThenPatternMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates generation of .dslr text:
 *  - WHEN: uses JSON templates via WhenPatternMatcher (with pre/append, catch-all on miss)
 *  - THEN: emits Java (println) via ThenPatternMatcher (no DSL RHS)
 */
public class DslBuilder {

    private final WhenPatternMatcher whenMatcher;
    private final ThenPatternMatcher thenMatcher;

    public DslBuilder() {
        this.whenMatcher = new WhenPatternMatcher();
        this.thenMatcher = new ThenPatternMatcher();
    }

    /** Build a single DSLR string containing all rules. */
    public String buildDslr(List<RuleRow> rows) {
        return rows.stream()
                .map(this::toDslrRule)
                .collect(Collectors.joining("\n\n"));
    }

    /** Build and write to file. */
    public void buildToFile(List<RuleRow> rows, Path outFile) {
        String dslr = buildDslr(rows);
        try {
            Files.createDirectories(outFile.getParent());
            Files.writeString(outFile, dslr);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write DSLR file: " + outFile, e);
        }
    }

    private String toDslrRule(RuleRow row) {
        String whenBlock = String.join("\n",
                whenMatcher.collectAll(row).stream()
                        .map(s -> "    " + s)
                        .toList()
        );

        String thenBlock = String.join("\n",
                thenMatcher.collectAll(row).stream()
                        .map(s -> "    " + s)
                        .toList()
        );

        return "rule \"" + row.ruleName() + "\"\n" +
                "when\n" +
                whenBlock + "\n" +
                "then\n" +
                thenBlock + "\n" +
                "end";
    }
}

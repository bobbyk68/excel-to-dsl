package uk.gov.hmrc.dslgen.dslgen;

import uk.gov.hmrc.dslgen.RuleRow;
import uk.gov.hmrc.dslgen.then.ThenPatternMatcher;
import uk.gov.hmrc.dslgen.when.WhenPatternMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** Orchestrates generation of .dslr text. */
public class DslBuilder {
    private final WhenPatternMatcher whenMatcher;
    private final ThenPatternMatcher thenMatcher;

    public DslBuilder() {
        this.whenMatcher = new WhenPatternMatcher();
        this.thenMatcher = new ThenPatternMatcher();
    }

    public String buildDslr(List<uk.gov.hmrc.dslgen.RuleRow> rows) {
        return rows.stream().map(this::toDslrRule).collect(Collectors.joining("\n\n"));
    }

    public void buildToFile(List<uk.gov.hmrc.dslgen.RuleRow> rows, Path outFile) {
        String dslr = buildDslr(rows);
        try {
            if (outFile.getParent()!=null) Files.createDirectories(outFile.getParent());
            Files.writeString(outFile, dslr);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write DSLR file: " + outFile, e);
        }
    }

    private String toDslrRule(RuleRow row) {
        String whenBlock = String.join("\n", whenMatcher.collectAll(row).stream().map(s -> "    " + s).toList());
        String thenBlock = String.join("\n", thenMatcher.collectAll(row).stream().map(s -> "    " + s).toList());
        return "rule \"" + row.ruleName() + "\"\nwhen\n" + whenBlock + "\nthen\n" + thenBlock + "\nend";
    }
}

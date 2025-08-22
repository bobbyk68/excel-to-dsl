package uk.gov.hmrc.dslgen.when;

import uk.gov.hmrc.dslgen.when.WhenPatternMatcher;
import uk.gov.hmrc.dslgen.then.ThenPatternMatcher;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class DslBuilder {

    private final WhenPatternMatcher whenMatcher = new WhenPatternMatcher();
    private final ThenPatternMatcher thenMatcher = new ThenPatternMatcher();

    public void build(List<RuleRow> rows, Path outputDslr) {
        StringBuilder sb = new StringBuilder();
        sb.append("package rules\n\n");
        sb.append("import uk.gov.hmrc.dslgen.model.Declaration;\n");
        sb.append("import uk.gov.hmrc.dslgen.model.Document;\n\n");

        for (RuleRow row : rows) {
            sb.append("rule \"").append(row.ruleName()).append("\"\n");
            sb.append("when\n");

            for (String w : whenMatcher.collectAll(row)) {
                sb.append("    ").append(w).append("\n");
            }

            sb.append("then\n");
            for (String t : thenMatcher.collectAll(row)) {
                sb.append("    ").append(t).append("\n");
            }
            sb.append("end\n\n");
        }

        try {
            Files.writeString(outputDslr, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write .dslr file", e);
        }
    }
}
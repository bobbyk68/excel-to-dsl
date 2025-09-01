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

package uk.gov.hmrc.rules.emit;

import uk.gov.hmrc.rules.core.model.RuleRow;

/** Renders DSLR using Excel literals; prepend is a visual no-op via .dsl mapping */
public final class DslBuilder {
    private static final String PREPEND = "Goods item exists"; // in .dsl: [when] Goods item exists =

    public void appendRule(StringBuilder out, RuleRow row, String leftLiteral, String rightLiteral) {
        String name = (row.businessRuleId() != null && !row.businessRuleId().isBlank())
                ? row.businessRuleId()
                : "Rule_" + Integer.toHexString(System.identityHashCode(row));

        out.append("rule \"").append(name).append("\"\n");
        if (row.errorCode() != null && !row.errorCode().isBlank())
            out.append("@ErrorCode(\"").append(row.errorCode()).append("\")\n");
        if (row.declarationType() != null && !row.declarationType().isBlank())
            out.append("@declarationType(\"").append(row.declarationType()).append("\")\n");
        if (row.procedureCategory() != null && !row.procedureCategory().isBlank())
            out.append("@procedureCategory(\"").append(row.procedureCategory()).append("\")\n");

        out.append("when\n")
                .append("    ").append(PREPEND).append("\n")
                .append("    - ").append(leftLiteral).append("\n")
                .append("    - ").append(rightLiteral).append("\n")
                .append("then\n")
                .append("    // action here\n")
                .append("end\n\n");
    }
}

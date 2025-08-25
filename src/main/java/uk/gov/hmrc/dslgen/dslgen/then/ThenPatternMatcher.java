package uk.gov.hmrc.dslgen.dslgen.then;

import uk.gov.hmrc.dslgen.RuleRow;
import uk.gov.hmrc.dslgen.support.PrePostConfig;

import java.util.ArrayList;
import java.util.List;

/** RHS is Java inside the DSLR (not DSL). */
public class ThenPatternMatcher {
    private final PrePostConfig prepost = new PrePostConfig();

    public List<String> collectAll(RuleRow row) {
        List<String> out = new ArrayList<>();
        out.addAll(prepost.thenPrepend());
        if (row.errorMessage() != null && !row.errorMessage().isBlank()) {
            out.add("System.out.println(\"" + row.errorMessage().replace("\"", "\\\"") + "\");");
        } else {
            out.add("// no RHS action");
        }
        out.addAll(prepost.thenAppend());
        return out;
    }
}

package uk.gov.hmrc.dslgen;

import java.nio.file.Path;
import java.util.List;

/** Minimal runner that writes target/generated-rules.dslr */
public class RuleGenerationRunner {
    public static void main(String[] args) {
        RuleRow row = new RuleRow() {
            public String ruleName() { return "BR236_1791_InvalidAuthType"; }
            public java.util.List<String> declarationTypes() { return java.util.List.of("A","D"); }
            public java.util.List<String> procedureCategories() { return java.util.List.of("Cat1"); }
            public String param() { return "123"; }
            public String errorMessage() { return "Invalid declaration: unsupported auth type"; }
            public java.util.List<String> whenCandidates() {
                return java.util.List.of(
                        "Declaration type oneof A,D",
                        "AuthorizationHolder.authorizationType.code must equal 123"
                );
            }
        };
        var builder = new DslBuilder();
        String dslr = builder.buildDslr(List.of(row));
        System.out.println(dslr);
        builder.buildToFile(List.of(row), Path.of("target/generated-rules.dslr"));
        System.out.println("✅ Wrote target/generated-rules.dslr");
    }
}

// uk/gov/hmrc/rules/core/RuleGeneratorRunner.java
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.hmrc.rules.core.index.RuleGraphIndex;
import uk.gov.hmrc.rules.core.model.*;
        import uk.gov.hmrc.rules.emit.DslBuilder;
import uk.gov.hmrc.rules.excel.ExcelReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

package uk.gov.hmrc.rules.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.hmrc.rules.core.model.*;
        import uk.gov.hmrc.rules.core.match.*;
        import uk.gov.hmrc.rules.index.CompositeIndex;
import uk.gov.hmrc.rules.emit.DslBuilder;
import uk.gov.hmrc.rules.excel.ExcelReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

public final class RuleGeneratorRunner {
    public static void main(String[] args) throws Exception {
        Path rulesJson = Path.of(args[0]);   // cleaned JSON (with (.*) / {1})
        Path excelPath = Path.of(args[1]);   // existing 9–10 column sheet
        String sheet   = args.length > 2 ? args[2] : "Rules";

        // 1) Load JSON
        ObjectMapper om = new ObjectMapper();
        Bundle bundle = om.readValue(Files.readString(rulesJson), Bundle.class);

        // 2) Compile atomics (anchor regex to whole-line)
        List<CompiledAjsontomic> compiled = bundle.atomic().stream()
                .map(a -> new CompiledAtomic(
                        a.id(),
                        Pattern.compile("^" + a.pattern() + "$"),
                        a.pattern(),
                        a.dsl()))
                .collect(Collectors.toList());
        AtomicMatcher matcher = new AtomicMatcher(compiled);

        // 3) Build composite ordered-pair index
        CompositeIndex compIndex = CompositeIndex.from(bundle.composite());

        // 4) Read Excel rows
        ExcelReader reader = new ExcelReader();
        List<RuleRow> rows = reader.read(excelPath.toString(), sheet);

        // 5) Build DSLR
        WhenPatternMatcher when = new WhenPatternMatcher(matcher, compIndex, new DslBuilder());
        String dslr = when.collectAll(rows);

        // 6) Write output
        Files.writeString(Path.of("out.dslr"), dslr);
        System.out.println("Wrote out.dslr (" + rows.size() + " rules)");
    }
}

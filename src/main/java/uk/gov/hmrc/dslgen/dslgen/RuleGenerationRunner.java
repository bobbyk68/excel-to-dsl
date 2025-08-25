package uk.gov.hmrc.dslgen.dslgen;

import uk.gov.hmrc.dslgen.DslBuilder;
import uk.gov.hmrc.dslgen.RuleRow;

import java.nio.file.Path;
import java.util.List;

/** Minimal runner that writes target/generated-rules.dslr */
public class RuleGenerationRunner {
    public static void main(String[] args) {
        uk.gov.hmrc.dslgen.RuleRow row = new RuleRow() {
            public String ruleName() { return "BR236_1791_InvalidAuthType"; }
            public List<String> declarationTypes() { return List.of("A","D"); }
            public List<String> procedureCategories() { return List.of("Cat1"); }
            public String param() { return "123"; }
            public String errorMessage() { return "Invalid declaration: unsupported auth type"; }
            public List<String> whenCandidates() {
                return List.of(
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

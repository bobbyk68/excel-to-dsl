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
 
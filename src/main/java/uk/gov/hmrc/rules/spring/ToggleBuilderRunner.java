package uk.gov.hmrc.rules.spring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import uk.gov.hmrc.rules.tools.ToggleGenerator;

import java.nio.file.Path;

@Component
public class ToggleBuilderRunner implements CommandLineRunner {

    @Value("${rules.dslr.root:src/main/resources/rules}")
    private String dslrRoot;

    @Value("${rules.dsl.root:src/main/resources/rules/dsl}")
    private String dslRoot;

    @Value("${rules.toggles.path:src/main/resources/config/rule-toggles.json}")
    private String togglesOut;

    @Value("${rules.toggles.build.onStartup:true}")
    private boolean buildOnStartup;

    @Override
    public void run(String... args) {
        if (buildOnStartup) {
            ToggleGenerator.build(Path.of(dslrRoot), Path.of(dslRoot), Path.of(togglesOut));
            System.out.println("rule-toggles.json regenerated.");
        }
    }
}

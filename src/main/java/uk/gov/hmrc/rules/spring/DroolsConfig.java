package uk.gov.hmrc.rules.spring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmrc.rules.engine.DroolsRuleEngine;

@Configuration
public class DroolsConfig {

    @Bean
    public DroolsRuleEngine droolsRuleEngine(
        @Value("${rules.dsl.root:rules/dsl}") String dslRoot,
        @Value("${rules.dslr.root:rules}")    String dslrRoot,
        @Value("${rules.toggles.path:src/main/resources/config/rule-toggles.json}") String togglesPath,
        @Value("${rules.files.onFilesystem:false}") boolean filesOnFilesystem
    ) {
        var eng = new DroolsRuleEngine(dslRoot, dslrRoot);
        eng.buildFromToggles(java.nio.file.Path.of(togglesPath), filesOnFilesystem);
        return eng;
    }
}

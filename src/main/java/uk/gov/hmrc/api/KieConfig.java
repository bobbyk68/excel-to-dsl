package uk.gov.hmrc.api;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmrc.rules.infra.BatchRegistry;

@Configuration
public class KieConfig {

    @Bean
    public BatchRegistry batchRegistry() {
        return new BatchRegistry();
    }
}

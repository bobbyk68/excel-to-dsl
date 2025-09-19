package uk.gov.hmrc.api;

import org.springframework.stereotype.Component;
import uk.gov.hmrc.rules.context.BatchRegistry;
import uk.gov.hmrc.rules.context.RuleRunContext;

@Component
public class RuleListenerFactory {
    private final BatchRegistry registry;
    public RuleListenerFactory(BatchRegistry registry) { this.registry = registry; }

    public RuleEventListener create(RuleRunContext ctx) {
        return new RuleEventListener(ctx, registry);
    }
}

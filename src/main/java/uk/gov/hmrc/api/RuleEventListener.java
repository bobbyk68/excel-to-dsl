package uk.gov.hmrc.api;

import org.kie.api.definition.rule.Rule;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import uk.gov.hmrc.rules.infra.BatchRegistry;

import java.util.Objects;

/**
 * Updates "last seen rule" so, if the batch times out, we know which rule was running.
 * No per-rule timers; no DRL metadata required.
 */
public class RuleEventListener extends DefaultAgendaEventListener {

    private final String opId;
    private final BatchRegistry batchRegistry;

    public RuleEventListener(String opId, BatchRegistry batchRegistry) {
        this.opId = Objects.requireNonNull(opId, "opId");
        this.batchRegistry = Objects.requireNonNull(batchRegistry);
    }

    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent event) {
        Rule rule = event.getMatch().getRule();
        String ruleName = rule.getName();
        // We use ruleName as the stable id to avoid modifying DRLs.
        String ruleId = ruleName;
        batchRegistry.updateLastRule(opId, ruleId, ruleName);
    }
}

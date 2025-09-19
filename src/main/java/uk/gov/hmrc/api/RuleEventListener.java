package uk.gov.hmrc.rules.listener;

import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import uk.gov.hmrc.rules.context.BatchRegistry;
import uk.gov.hmrc.rules.context.RuleRunContext;

public final class RuleEventListener extends DefaultAgendaEventListener {
    private final RuleRunContext ctx;
    private final BatchRegistry registry;

    RuleEventListener(RuleRunContext ctx, BatchRegistry registry) {
        this.ctx = ctx;
        this.registry = registry;
    }

    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent e) {
        // Example: lightweight correlation-friendly logging
        // (No MDC required)
        // log.debug("opId={} firing rule={}", ctx.opId(), e.getMatch().getRule().getName());
        // If you ever need registry (e.g. to annotate current rule), it's here.
        registry.get(ctx.opId()); // optional use
    }
}

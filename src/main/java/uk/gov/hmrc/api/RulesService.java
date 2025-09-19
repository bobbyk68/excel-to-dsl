package uk.gov.hmrc.rules.service;

import org.kie.api.runtime.KieBase;
import org.kie.api.runtime.KieSession;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import uk.gov.hmrc.rules.context.BatchRegistry;
import uk.gov.hmrc.rules.context.RuleRunContext;
import uk.gov.hmrc.rules.listener.RuleListenerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class RulesService {
    private final KieBase kieBase;
    private final BatchRegistry registry;
    private final RuleListenerFactory listenerFactory;

    public RulesService(KieBase kieBase, BatchRegistry registry, RuleListenerFactory listenerFactory) {
        this.kieBase = kieBase;
        this.registry = registry;
        this.listenerFactory = listenerFactory;
    }

    @Async("rulesExecutor")
    public CompletableFuture<ReceiveValidationResults> executeRules(DeclarationValidationRequest req) {
        // Create once at the boundary (caller can also supply opId if you have one)
        String opId  = UUID.randomUUID().toString();
        String label = "DeclarationValidation";
        RuleRunContext ctx = new RuleRunContext(opId, label);
        registry.register(ctx);

        KieSession ks = null;
        try {
            ks = kieBase.newKieSession();
            ks.addEventListener(listenerFactory.create(ctx));   // <-- clean injection

            // run your pipeline (sync inside @Async)
            ReceiveValidationResults res = runRules(ks, req, ctx);
            registry.complete(opId);

            return CompletableFuture.completedFuture(res);
        } catch (RuntimeException ex) {
            // attach your timeout/cooperative halt logic if needed
            throw ex;
        } finally {
            if (ks != null) ks.dispose();
            registry.remove(opId);
        }
    }

    private ReceiveValidationResults runRules(KieSession ks, DeclarationValidationRequest req, RuleRunContext ctx) {
        // insert facts, fireAllRules, etc.
        ks.fireAllRules();
        return new ReceiveValidationResults();
    }
}

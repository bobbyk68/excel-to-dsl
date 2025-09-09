package uk.gov.hmrc.api;

import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import uk.gov.hmrc.rules.infra.BatchRegistry;
import uk.gov.hmrc.rules.listener.RuleEventListener;
import uk.gov.hmrc.rules.logging.RuleLog;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class RulesService {

    private final KieContainer kieContainer;
    private final Executor rulesBatchExecutor;
    private final BatchRegistry batchRegistry;

    // configure your single "standard" batch timeout here (ms)
    private final long batchTimeoutMs = 5_000L;

    public RulesService(KieContainer kieContainer,
                        Executor rulesBatchExecutor,
                        BatchRegistry batchRegistry) {
        this.kieContainer = Objects.requireNonNull(kieContainer);
        this.rulesBatchExecutor = Objects.requireNonNull(rulesBatchExecutor);
        this.batchRegistry = Objects.requireNonNull(batchRegistry);
    }

    /**
     * Runs ALL rules with a single standard timeout via orTimeout(...).
     * No DRL changes, no per-rule timers.
     */
    public CompletableFuture<Void> runRules(ValidateDeclaration decl) {
        // Ensure opId once per batch (MDC only for cosmetics; registry holds the truth)
        String opId = MDC.get("opId");
        if (opId == null || opId.isBlank()) {
            opId = UUID.randomUUID().toString();
            MDC.put("opId", opId);
        }
        final String batchOpId = opId;

        // Start batch tracking
        batchRegistry.start(batchOpId);

        CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
            KieSession ksession = kieContainer.newKieSession();
            try {
                // Listener updates lastRuleId/Name in beforeMatchFired
                ksession.addEventListener(new RuleEventListener(batchOpId, batchRegistry));

                ksession.insert(decl);
                ksession.fireAllRules();

                // Batch finished OK
                long elapsed = batchRegistry.elapsedMs(batchOpId);
                RuleLog.okBatch(batchOpId, elapsed);
            } finally {
                try { ksession.dispose(); } catch (Exception ignored) { }
                batchRegistry.finish(batchOpId);
            }
        }, rulesBatchExecutor);

        // Single standard timeout for the whole batch
        return cf.orTimeout(batchTimeoutMs, TimeUnit.MILLISECONDS)
                 .exceptionally(err -> {
                     long elapsed = batchRegistry.elapsedMs(batchOpId);
                     var be = batchRegistry.get(batchOpId); // may be null if finished
                     String lastRuleId = (be == null) ? "" : be.lastRuleId;
                     String lastRuleName = (be == null) ? "" : be.lastRuleName;

                     RuleLog.timeoutBatch(batchOpId, lastRuleId, lastRuleName, elapsed < 0 ? -1 : elapsed);

                     // propagate as RuntimeException so the caller sees the timeout if they care
                     throw (err instanceof RuntimeException re) ? re : new RuntimeException(err);
                 });
    }
}

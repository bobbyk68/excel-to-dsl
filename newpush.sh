#!/bin/bash
set -e

BASE=src/main/java/uk/gov/hmrc/rules
mkdir -p $BASE/context $BASE/listener $BASE/service $BASE/web $BASE/model

############################################
# context/BatchContext.java (state machine + diagnostics)
############################################
cat > $BASE/context/BatchContext.java <<'EOF'
package uk.gov.hmrc.rules.context;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class BatchContext {

    public enum Status { NEW, COMPLETED, TIMED_OUT }
    public enum Phase  { STARTED, FACTS_INSERTED, ACTIVATING, FIRING }

    private final String opId;
    private final String label;
    private final long startNanos = System.nanoTime();
    private final Instant startTime = Instant.now();

    private final AtomicReference<Status> status = new AtomicReference<>(Status.NEW);
    private final AtomicLong endNanos = new AtomicLong(0L);

    // Diagnostics
    private final AtomicReference<String> lastActivationRule = new AtomicReference<>(null);
    private final AtomicReference<String> currentRule       = new AtomicReference<>(null);
    private final AtomicReference<Phase>  phase             = new AtomicReference<>(Phase.STARTED);

    public BatchContext(String opId, String label) {
        this.opId = opId;
        this.label = label;
    }

    public String opId()   { return opId; }
    public String label()  { return label; }
    public Status status() { return status.get(); }

    public boolean tryComplete() {
        if (status.compareAndSet(Status.NEW, Status.COMPLETED)) {
            endNanos.compareAndSet(0L, System.nanoTime());
            return true;
        }
        return false;
    }

    public boolean tryTimeout() {
        if (status.compareAndSet(Status.NEW, Status.TIMED_OUT)) {
            endNanos.compareAndSet(0L, System.nanoTime());
            return true;
        }
        return false;
    }

    public long elapsedMillis() {
        long end = endNanos.get();
        if (end == 0L) end = System.nanoTime();
        long diff = end - startNanos;
        return diff <= 0 ? 0 : diff / 1_000_000;
    }

    // ---- Diagnostics helpers ----
    public void setPhase(Phase p) { phase.set(p); }
    public Phase phase() { return phase.get(); }

    public void setLastActivationRule(String ruleName) { lastActivationRule.set(ruleName); }
    public void setCurrentRule(String ruleName)        { currentRule.set(ruleName); }

    public String lastActivationRule() { return lastActivationRule.get(); }
    public String currentRule()        { return currentRule.get(); }

    /** Best-effort rule name for timeouts / diagnostics. */
    public String ruleForDiagnostics() {
        String firing = currentRule.get();
        if (firing != null) return firing;
        String pending = lastActivationRule.get();
        return (pending != null) ? pending : "no-activation";
    }
}
EOF

############################################
# context/BatchRegistry.java (singleton)
############################################
cat > $BASE/context/BatchRegistry.java <<'EOF'
package uk.gov.hmrc.rules.context;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public final class BatchRegistry {

    private final ConcurrentMap<String, BatchContext> byOpId = new ConcurrentHashMap<>();

    public BatchContext register(String opId, String label) {
        return byOpId.computeIfAbsent(opId, k -> new BatchContext(opId, label));
    }

    public Optional<BatchContext> get(String opId) {
        return Optional.ofNullable(byOpId.get(opId));
    }

    public void remove(String opId) {
        byOpId.remove(opId);
    }
}
EOF

############################################
# model/DeclarationValidationRequest.java
############################################
cat > $BASE/model/DeclarationValidationRequest.java <<'EOF'
package uk.gov.hmrc.rules.model;

public class DeclarationValidationRequest {
    // minimal placeholder fields; extend as needed
    private String declarationId;

    public DeclarationValidationRequest() {}

    public DeclarationValidationRequest(String declarationId) {
        this.declarationId = declarationId;
    }

    public String getDeclarationId() { return declarationId; }
    public void setDeclarationId(String declarationId) { this.declarationId = declarationId; }
}
EOF

############################################
# model/ReceiveValidationResults.java
############################################
cat > $BASE/model/ReceiveValidationResults.java <<'EOF'
package uk.gov.hmrc.rules.model;

public class ReceiveValidationResults {
    private String opId;
    private boolean success;

    public ReceiveValidationResults() {}

    public ReceiveValidationResults(String opId, boolean success) {
        this.opId = opId;
        this.success = success;
    }

    public String getOpId() { return opId; }
    public void setOpId(String opId) { this.opId = opId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
EOF

############################################
# listener/RuleEventListener.java (stamps activation + firing; logs session id)
############################################
cat > $BASE/listener/RuleEventListener.java <<'EOF'
package uk.gov.hmrc.rules.listener;

import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmrc.rules.context.BatchContext;
import uk.gov.hmrc.rules.context.BatchRegistry;

public final class RuleEventListener extends DefaultAgendaEventListener {

    private static final Logger log = LoggerFactory.getLogger(RuleEventListener.class);

    private final BatchRegistry registry; // injected at service ctor
    private final String opId;

    public RuleEventListener(BatchRegistry registry, String opId) {
        this.registry = registry;
        this.opId = opId;
    }

    @Override
    public void matchCreated(MatchCreatedEvent e) {
        registry.get(opId).ifPresent(ctx -> {
            ctx.setLastActivationRule(e.getMatch().getRule().getName());
            ctx.setPhase(BatchContext.Phase.ACTIVATING);
        });
        // diag: session identity
        int sid = System.identityHashCode(e.getKieRuntime());
        log.debug("opId={} [ksession#{}] matchCreated rule={}", opId, sid, e.getMatch().getRule().getName());
    }

    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent e) {
        registry.get(opId).ifPresent(ctx -> {
            ctx.setCurrentRule(e.getMatch().getRule().getName());
            ctx.setPhase(BatchContext.Phase.FIRING);
        });
        int sid = System.identityHashCode(e.getKieRuntime());
        log.debug("opId={} [ksession#{}] beforeMatchFired rule={}", opId, sid, e.getMatch().getRule().getName());
    }
}
EOF

############################################
# service/RulesService.java (logs session id; sets phases)
############################################
cat > $BASE/service/RulesService.java <<'EOF'
package uk.gov.hmrc.rules.service;

import org.kie.api.runtime.KieBase;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import uk.gov.hmrc.rules.context.BatchContext;
import uk.gov.hmrc.rules.context.BatchRegistry;
import uk.gov.hmrc.rules.listener.RuleEventListener;
import uk.gov.hmrc.rules.model.DeclarationValidationRequest;
import uk.gov.hmrc.rules.model.ReceiveValidationResults;

import java.util.concurrent.CompletableFuture;

@Service
public class RulesService {

    private static final Logger log = LoggerFactory.getLogger(RulesService.class);

    private final KieBase kieBase;
    private final BatchRegistry registry;

    public RulesService(KieBase kieBase, BatchRegistry registry) {
        this.kieBase = kieBase;
        this.registry = registry;
    }

    @Async("rulesExecutor")
    public CompletableFuture<ReceiveValidationResults> executeRules(String opId,
                                                                    DeclarationValidationRequest req) {
        KieSession ks = null;
        try {
            ks = kieBase.newKieSession();
            int sid = System.identityHashCode(ks);
            log.debug("opId={} created KieSession [ksession#{}]", opId, sid);

            // Phase hint after fact insertion (add your real inserts/globals where needed)
            registry.get(opId).ifPresent(bc -> bc.setPhase(BatchContext.Phase.FACTS_INSERTED));

            ks.addEventListener(new RuleEventListener(registry, opId));

            ks.fireAllRules();

            ReceiveValidationResults result = new ReceiveValidationResults(opId, true);
            return CompletableFuture.completedFuture(result);

        } finally {
            if (ks != null) ks.dispose();
        }
    }
}
EOF

############################################
# web/RulesController.java (endpoint blocks; logs timeout diagnostics)
############################################
cat > $BASE/web/RulesController.java <<'EOF'
package uk.gov.hmrc.rules.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmrc.rules.context.*;
import uk.gov.hmrc.rules.model.DeclarationValidationRequest;
import uk.gov.hmrc.rules.model.ReceiveValidationResults;
import uk.gov.hmrc.rules.service.RulesService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/rules")
public class RulesController {

    private static final Logger log = LoggerFactory.getLogger(RulesController.class);

    private final RulesService rulesService;
    private final BatchRegistry batchRegistry;

    public RulesController(RulesService rulesService, BatchRegistry batchRegistry) {
        this.rulesService = rulesService;
        this.batchRegistry = batchRegistry;
    }

    @PostMapping("/validate")
    public ReceiveValidationResults validate(@RequestBody DeclarationValidationRequest req) throws Exception {
        final long timeoutMs = 1500L;

        String opId = UUID.randomUUID().toString();
        String label = "DeclarationValidation";
        BatchContext bc = batchRegistry.register(opId, label);

        CompletableFuture<ReceiveValidationResults> cf = rulesService.executeRules(opId, req);

        try {
            ReceiveValidationResults res = cf.get(timeoutMs, TimeUnit.MILLISECONDS);
            boolean won = bc.tryComplete();
            log.debug("opId={} complete {}", opId, won ? "WON" : "NO-OP");
            return res;

        } catch (java.util.concurrent.TimeoutException te) {
            boolean won = bc.tryTimeout();
            String rule = bc.ruleForDiagnostics();
            BatchContext.Phase phase = bc.phase();
            long elapsed = bc.elapsedMillis();

            log.warn("opId={} TIMED OUT after {} ms (phase={}, rule={}) won={}",
                    opId, elapsed, phase, rule, won ? "WON" : "NO-OP");

            // best-effort stop
            cf.cancel(true);
            throw te;

        } catch (java.util.concurrent.ExecutionException ee) {
            Throwable root = (ee.getCause() != null) ? ee.getCause() : ee;
            log.error("opId={} execution error {}", opId, root.toString(), root);
            if (root instanceof RuntimeException re) throw re;
            throw new RuntimeException(root);

        } finally {
            batchRegistry.remove(opId);
            log.debug("opId={} registry entry removed", opId);
        }
    }
}
EOF

echo "✅ Files created/updated under $BASE"

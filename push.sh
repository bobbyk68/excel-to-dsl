#!/bin/bash
set -e

BASE=src/main/java/uk/gov/hmrc/rules
mkdir -p $BASE/context $BASE/listener $BASE/service $BASE/web

# BatchContext.java
cat > $BASE/context/BatchContext.java <<'EOF'
package uk.gov.hmrc.rules.context;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class BatchContext {

    public enum Status { NEW, COMPLETED, TIMED_OUT }

    private final String opId;
    private final String label;
    private final long startNanos = System.nanoTime();
    private final Instant startTime = Instant.now();

    private final AtomicReference<Status> status = new AtomicReference<>(Status.NEW);
    private final AtomicLong endNanos = new AtomicLong(0L);

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
}
EOF

# BatchRegistry.java
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

# RuleEventListener.java
cat > $BASE/listener/RuleEventListener.java <<'EOF'
package uk.gov.hmrc.rules.listener;

import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import uk.gov.hmrc.rules.context.BatchRegistry;

public final class RuleEventListener extends DefaultAgendaEventListener {

    private final BatchRegistry registry;
    private final String opId;

    public RuleEventListener(BatchRegistry registry, String opId) {
        this.registry = registry;
        this.opId = opId;
    }

    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent e) {
        registry.get(opId).ifPresent(ctx -> {
            // Example log
            // log.debug("opId={} firing rule={}", opId, e.getMatch().getRule().getName());
        });
    }
}
EOF

# RulesService.java
cat > $BASE/service/RulesService.java <<'EOF'
package uk.gov.hmrc.rules.service;

import org.kie.api.runtime.KieBase;
import org.kie.api.runtime.KieSession;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import uk.gov.hmrc.rules.listener.RuleEventListener;
import uk.gov.hmrc.rules.context.BatchRegistry;

import java.util.concurrent.CompletableFuture;

@Service
public class RulesService {

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
            ks.addEventListener(new RuleEventListener(registry, opId));

            // --- rule pipeline ---
            ks.fireAllRules();

            ReceiveValidationResults result = new ReceiveValidationResults(); // TODO: build properly
            return CompletableFuture.completedFuture(result);

        } finally {
            if (ks != null) ks.dispose();
        }
    }
}
EOF

# RulesController.java
cat > $BASE/web/RulesController.java <<'EOF'
package uk.gov.hmrc.rules.web;

import org.springframework.web.bind.annotation.*;
import uk.gov.hmrc.rules.context.*;
import uk.gov.hmrc.rules.service.RulesService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/rules")
public class RulesController {

    private final RulesService rulesService;
    private final BatchRegistry batchRegistry;

    public RulesController(RulesService rulesService, BatchRegistry batchRegistry) {
        this.rulesService = rulesService;
        this.batchRegistry = batchRegistry;
    }

    @PostMapping("/validate")
    public ReceiveValidationResults validate(@RequestBody DeclarationValidationRequest req) throws Exception {
        final long timeoutMs = 1500;

        String opId = UUID.randomUUID().toString();
        String label = "DeclarationValidation";
        BatchContext bc = batchRegistry.register(opId, label);

        CompletableFuture<ReceiveValidationResults> cf = rulesService.executeRules(opId, req);

        try {
            ReceiveValidationResults res = cf.get(timeoutMs, TimeUnit.MILLISECONDS);
            bc.tryComplete();
            return res;
        } catch (java.util.concurrent.TimeoutException te) {
            bc.tryTimeout();
            cf.cancel(true);
            throw te;
        } catch (java.util.concurrent.ExecutionException ee) {
            Throwable root = (ee.getCause() != null) ? ee.getCause() : ee;
            if (root instanceof RuntimeException re) throw re;
            throw new RuntimeException(root);
        } finally {
            batchRegistry.remove(opId);
        }
    }
}
EOF

echo "✅ Files created under $BASE"

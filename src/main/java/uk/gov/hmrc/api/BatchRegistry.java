package uk.gov.hmrc.rules.context;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public final class BatchRegistry {
    private final ConcurrentMap<String, BatchContext> byOpId = new ConcurrentHashMap<>();

    public BatchContext register(RuleRunContext ctx) {
        return byOpId.computeIfAbsent(ctx.opId(), k -> new BatchContext(ctx.opId(), ctx.label()));
    }
    public Optional<BatchContext> get(String opId) {
        return Optional.ofNullable(byOpId.get(opId));
    }
    public void markTimeout(String opId) { get(opId).ifPresent(BatchContext::tryTimeout); }
    public void complete(String opId)    { get(opId).ifPresent(BatchContext::tryComplete); }
    public void remove(String opId)      { byOpId.remove(opId); }
}

package uk.gov.hmrc.api;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class BatchRegistry {

    public static final class BatchEntry {
        public final String opId;
        public final long startNanos;
        public volatile String lastRuleId = "";
        public volatile String lastRuleName = "";
        public volatile boolean done = false;

        public BatchEntry(String opId, long startNanos) {
            this.opId = Objects.requireNonNull(opId, "opId");
            this.startNanos = startNanos;
        }
    }

    private final ConcurrentHashMap<String, BatchEntry> batches = new ConcurrentHashMap<>();

    public BatchEntry start(String opId) {
        BatchEntry e = new BatchEntry(opId, System.nanoTime());
        batches.put(opId, e);
        return e;
    }

    public BatchEntry get(String opId) { return opId == null ? null : batches.get(opId); }

    public void updateLastRule(String opId, String ruleId, String ruleName) {
        BatchEntry e = get(opId);
        if (e != null) {
            e.lastRuleId = ruleId == null ? "" : ruleId;
            e.lastRuleName = ruleName == null ? "" : ruleName;
        }
    }

    public void finish(String opId) {
        BatchEntry e = get(opId);
        if (e != null) e.done = true;
        if (opId != null) batches.remove(opId);
    }

    public long elapsedMs(String opId) {
        BatchEntry e = get(opId);
        if (e == null) return -1L;
        return (System.nanoTime() - e.startNanos) / 1_000_000L;
    }
}

package uk.gov.hmrc.rules;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class CurrentRuleRegistry {
    private CurrentRuleRegistry() {}
    public static final class RuleInfo {
        public final String pkg, name;
        public RuleInfo(String pkg, String name) { this.pkg = pkg; this.name = name; }
        @Override public String toString() { return (pkg == null ? "" : pkg + ".") + name; }
    }

    private static final ConcurrentHashMap<String, AtomicReference<RuleInfo>> MAP = new ConcurrentHashMap<>();

    public static AtomicReference<RuleInfo> register(String opId) {
        return MAP.computeIfAbsent(opId, k -> new AtomicReference<>(null));
    }
    public static void update(String opId, String pkg, String name) {
        AtomicReference<RuleInfo> ref = MAP.get(opId);
        if (ref != null) ref.set(new RuleInfo(pkg, name));
    }
    public static RuleInfo snapshot(String opId) {
        AtomicReference<RuleInfo> ref = MAP.get(opId);
        return ref != null ? ref.get() : null;
    }
    public static void clear(String opId) { MAP.remove(opId); }
}

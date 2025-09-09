package uk.gov.hmrc.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RuleLog {
    private static final Logger log = LoggerFactory.getLogger("RULES");

    private RuleLog() {}

    public static void timeoutBatch(String opId, String lastRuleId, String lastRuleName, long elapsedMs) {
        log.warn("type=batch level=WARN opId={} lastRuleId={} lastRuleName='{}' elapsedMs={}",
                 opId, lastRuleId, lastRuleName, elapsedMs);
    }

    public static void okBatch(String opId, long elapsedMs) {
        log.debug("type=batch level=DEBUG opId={} status=OK elapsedMs={}", opId, elapsedMs);
    }
}

package uk.gov.hmrc.async;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import uk.gov.hmrc.rules.CurrentRuleRegistry;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AsyncTimeoutLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(AsyncTimeoutLoggingAspect.class);

    @Value("${async.default-timeout:PT2S}")
    private Duration defaultTimeout;

    @Around("@annotation(async) && execution(java.util.concurrent.CompletableFuture *(..))")
    public Object addTimeoutAndLog(ProceedingJoinPoint pjp, Async async) throws Throwable {
        final String opId = UUID.randomUUID().toString();
        MDC.put("opId", opId);                // make opId available to @Async thread via TaskDecorator
        try {
            Object ret = pjp.proceed();
            if (!(ret instanceof CompletableFuture<?> original)) return ret;

            final long startNs = System.nanoTime();
            final long limitMs = defaultTimeout.toMillis();
            final String op = pjp.getSignature().toShortString();

            original.orTimeout(limitMs, TimeUnit.MILLISECONDS)
                    .whenComplete((v, ex) -> {
                        try {
                            if (ex instanceof TimeoutException) {
                                var info = CurrentRuleRegistry.snapshot(opId);
                                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                                log.warn("op={} outcome=TIMEOUT elapsedMs={} limitMs={} opId={} lastRule={}",
                                        op, elapsedMs, limitMs, opId, info == null ? "<unknown>" : info);
                                // Optional: try to stop work if your pipeline cooperates:
                                // original.cancel(true);
                            }
                        } finally {
                            CurrentRuleRegistry.clear(opId); // avoid leaks
                        }
                    });

            return ret;
        } finally {
            MDC.remove("opId"); // clean on caller thread
        }
    }
}

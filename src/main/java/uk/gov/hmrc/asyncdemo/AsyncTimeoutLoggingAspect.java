package uk.gov.hmrc.asyncdemo;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10) // run after @Async interceptor
public class AsyncTimeoutLoggingAspect {

  @Value("${async.default-timeout:PT2S}")
  private Duration defaultTimeout;

  @Around("@annotation(org.springframework.scheduling.annotation.Async) && execution(java.util.concurrent.CompletableFuture *(..))")
  public Object addTimeout(ProceedingJoinPoint pjp) throws Throwable {
    Object ret = pjp.proceed();
    if (!(ret instanceof CompletableFuture<?> original)) return ret;

    final long startNs = System.nanoTime();
    final long limitMs = defaultTimeout.toMillis();

    // 1) Arm the timeout ON the original future
    original.orTimeout(limitMs, TimeUnit.MILLISECONDS);

    // 2) Only log when the timeout actually fires
    original.whenComplete((v, ex) -> {
      if (ex instanceof TimeoutException) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        LoggerFactory.getLogger(getClass()).warn(
          "op={} outcome=TIMEOUT elapsedMs={} limitMs={} thread={}",
          pjp.getSignature().toShortString(), elapsedMs, limitMs, Thread.currentThread().getName()
        );
        // Optionally try to stop the work if it honors interrupts:
        // original.cancel(true);
      }
    });

    // Return the same instance the service created, now with timeout+logging attached
    return ret;
  }
}

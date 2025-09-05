package uk.gov.hmrc.asyncdemo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ThreadProbeAspect {
    private static final Logger log = LoggerFactory.getLogger(ThreadProbeAspect.class);

    // Intercept any method annotated with @Async within your app packages
    @Around("(@annotation(org.springframework.scheduling.annotation.Async) " +
            " || @within(org.springframework.scheduling.annotation.Async)) " +
            " && execution(* uk.gov.hmrc..*(..))")
    public Object logThread(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        String before = Thread.currentThread().getName();
        log.info(">>> Entering async method {} on thread {}", method, before);
        try {
            Object result = pjp.proceed();
            return result;
        } finally {
            String after = Thread.currentThread().getName();
            log.info("<<< Exiting async method {} on thread {}", method, after);
        }
    }
}

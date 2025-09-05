package uk.gov.hmrc.asyncdemo;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class DemoAsync {

    @Async("testExecutor")
    public CompletableFuture<String> performAsync() {
        // No logging here—Aspect (test-only) captures thread name for us.
        return CompletableFuture.completedFuture("done");
    }

    @Async("testExecutor")
    public CompletableFuture<String> slowThenTimeout(long sleepMillis, long timeoutMillis) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(sleepMillis);
                return "OK";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }).orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}

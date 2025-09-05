package uk.gov.hmrc.dslgen;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class AsyncService {

    @Async("testExecutor")
    public CompletableFuture<String> threadName() {
        return CompletableFuture.supplyAsync(
            () -> Thread.currentThread().getName()
        );
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

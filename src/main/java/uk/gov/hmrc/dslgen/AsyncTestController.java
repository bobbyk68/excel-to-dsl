package uk.gov.hmrc.dslgen;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/async")
public class AsyncTestController {

    private final AsyncService service;

    public AsyncTestController(AsyncService service) {
        this.service = service;
    }

    @GetMapping("/thread")
    public Map<String, String> threadProbe() {
        String controllerThread = Thread.currentThread().getName();
        String asyncThread = service.threadName().join(); // wait just for demo
        return Map.of(
            "controllerThread", controllerThread,
            "asyncThread", asyncThread
        );
    }

    @GetMapping("/timeout")
    public Map<String, Object> timeoutProbe(
            @RequestParam(defaultValue = "1000") long sleepMillis,
            @RequestParam(defaultValue = "100") long timeoutMillis) {

        CompletableFuture<String> f = service.slowThenTimeout(sleepMillis, timeoutMillis);
        try {
            String result = f.join(); // will throw CompletionException on timeout
            return Map.of("status", "OK", "result", result);
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                // Make this obviously visible to a human/manual tester
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                        "Async work timed out after " + timeoutMillis + " ms", e.getCause());
            }
            throw e;
        }
    }
}

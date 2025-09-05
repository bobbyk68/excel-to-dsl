package uk.gov.hmrc.dslgen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class AsyncServiceTest {

    @Autowired
    private AsyncService service;

    @Test
    void runsOnDifferentThread() throws Exception {
        String testThread = Thread.currentThread().getName(); // typically "main"
        String asyncThread = service.threadName().get();

        // Prove it's not the same thread and that it's our executor thread
        assertNotEquals(testThread, asyncThread);
        assertTrue(asyncThread.startsWith("async-"),
            "Expected executor thread with prefix 'async-' but was: " + asyncThread);
    }

    @Test
    void timesOutWhenTooSlow() {
        long sleepMillis = 1000;     // simulate slow work
        long timeoutMillis = 100;    // force timeout
        var future = service.slowThenTimeout(sleepMillis, timeoutMillis);

        // get() wraps the cause in ExecutionException; unwrap to assert TimeoutException
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause() instanceof TimeoutException,
            "Expected TimeoutException but got: " + ex.getCause());
    }

    @Test
    void completesWhenFastEnough() throws Exception {
        long sleepMillis = 50;
        long timeoutMillis = 500;
        var future = service.slowThenTimeout(sleepMillis, timeoutMillis);

        assertEquals("OK", future.get()); // no exception -> no timeout
    }
}

package uk.gov.hmrc.asyncdemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@Import({ TestAsyncConfig.class, ThreadProbeAspect.class })
class AsyncBehaviourTest {

    // ⬇️ Replace DemoAsync with *your* service class & async method(s)
    @Autowired
    private DemoAsync demo;

    @Test
    void provesMethodRunsOnNonMainThread(CapturedOutput output) throws Exception {
        // Call your real async method here instead of demo.performAsync()
        demo.performAsync().get();

        // Assert the thread name captured by the test-only Aspect
        assertThat(output.getOut())
            .contains(">>> Entering async method")
            .contains("test-async-");       // from ThreadNamePrefix
    }

    @Test
    void provesTimeoutHappens() {
        var future = demo.slowThenTimeout(300, 50); // force timeout: sleep > timeout
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause() instanceof TimeoutException);
    }

    @Test
    void completesIfFastEnough() throws Exception {
        var future = demo.slowThenTimeout(20, 500);
        assertEquals("OK", future.get());
    }
}

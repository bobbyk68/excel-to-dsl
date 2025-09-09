package uk.gov.hmrc.api;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> prev = MDC.getCopyOfContextMap();
            try {
                if (context != null) MDC.setContextMap(context);
                runnable.run();
            } finally {
                if (prev == null) MDC.clear(); else MDC.setContextMap(prev);
            }
        };
    }
}

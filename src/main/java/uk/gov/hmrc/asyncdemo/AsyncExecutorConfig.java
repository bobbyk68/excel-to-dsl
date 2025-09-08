package uk.gov.hmrc.asyncdemo;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

@Configuration
public class AsyncExecutorConfig {
  @Bean("rulesExecutor")
  public ThreadPoolTaskExecutor rulesExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setThreadNamePrefix("rulesExecutor-");
    exec.setCorePoolSize(4);
    exec.setMaxPoolSize(16);
    exec.setTaskDecorator(mdcPropagating());
    exec.initialize();
    return exec;
  }

  private TaskDecorator mdcPropagating() {
    return runnable -> {
      Map<String,String> contextMap = MDC.getCopyOfContextMap();
      return () -> {
        Map<String,String> previous = MDC.getCopyOfContextMap();
        try {
          if (contextMap != null) MDC.setContextMap(contextMap); else MDC.clear();
          runnable.run();
        } finally {
          if (previous != null) MDC.setContextMap(previous); else MDC.clear();
        }
      };
    };
  }
}

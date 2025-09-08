package uk.gov.hmrc.asyncdemo;

import org.kie.api.definition.rule.Rule;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.KieSession;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class ValidationService {

  private final KieSession ksession;

  public ValidationService(KieSession ksession) { this.ksession = ksession; }

  @Async("rulesExecutor")
  public CompletableFuture<Void> validate(Object decl) {
    return CompletableFuture.runAsync(() -> {
      String opId = MDC.get("opId"); // set by aspect, propagated by TaskDecorator
      if (opId != null) CurrentRuleRegistry.register(opId);

      ksession.addEventListener(new DefaultAgendaEventListener() {
        @Override public void beforeMatchFired(BeforeMatchFiredEvent e) {
          Rule r = e.getMatch().getRule();
          CurrentRuleRegistry.update(opId, r.getPackageName(), r.getName());
        }
      });

      // insert facts, etc...
      // ksession.insert(decl);
      ksession.fireAllRules();
    });
  }
}

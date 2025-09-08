package uk.gov.hmrc.asyncdemo;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class EmailService {

  @Async("rulesExecutor")
  @TimeoutLogFields({"#userId", "#request.declarationId", "'type='+#type"})
  public CompletableFuture<String> sendEmailAsync(String userId, RequestDto request, String type) {
    return CompletableFuture.supplyAsync(() -> {
      // ... do work ...
      return "sent";
    });
  }
}

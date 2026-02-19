package io.github.mrozowski.orchestration;

import java.time.Duration;
import java.time.Instant;

public record StepExecutionMetadata(
    String stepName,
    Instant startTime,
    Instant endTime,
    Duration processingTime,
    boolean success,
    Throwable exception,
    int attempts
) {

  public static StepExecutionMetadata success(String stepName, Instant startTime, Instant endTime, int attempts) {
    Duration stepProcessingTime = Duration.between(startTime, endTime);
    return  new StepExecutionMetadata(stepName, startTime, endTime, stepProcessingTime, true,null, attempts);
  }

  public static StepExecutionMetadata failed(String stepName, Instant startTime, Instant endTime, int attempts, Throwable exception) {
    Duration stepProcessingTime = Duration.between(startTime, endTime);
    return  new StepExecutionMetadata(stepName, startTime, endTime, stepProcessingTime, false, exception, attempts);
  }
}

package io.github.mrozowski.orchestration;

import java.util.List;

public record OrchestrationResult(Status status, OrchestrationContext context, List<StepExecutionMetadata> steps) {

  public enum Status {
    SUCCESS,
    FAILED,
    PARTIAL
  }

  public OrchestrationResult(Status status,
                             OrchestrationContext context,
                             List<StepExecutionMetadata> steps) {
    this.status = status;
    this.context = context;
    this.steps = List.copyOf(steps);
  }
}
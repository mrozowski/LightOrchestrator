package io.github.mrozowski.orchestration;

import java.util.List;

public record OrchestrationResult(Status status, OrchestrationContext context, List<StepExecution> steps) {

  public enum Status {
    SUCCESS,
    FAILED,
    PARTIAL
  }

  public OrchestrationResult(Status status,
                             OrchestrationContext context,
                             List<StepExecution> steps) {
    this.status = status;
    this.context = context;
    this.steps = List.copyOf(steps);
  }
}
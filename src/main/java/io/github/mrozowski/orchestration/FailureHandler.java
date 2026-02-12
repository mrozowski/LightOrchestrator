package io.github.mrozowski.orchestration;

@FunctionalInterface
public interface FailureHandler {

  FailureStrategy onFailure(Throwable exception,
                            OrchestrationContext context);

  static FailureHandler stop() {
    return (ex, ctx) -> FailureStrategy.STOP;
  }

  static FailureHandler cont() {
    return (ex, ctx) -> FailureStrategy.CONTINUE;
  }
}
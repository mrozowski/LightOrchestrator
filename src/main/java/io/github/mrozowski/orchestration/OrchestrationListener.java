package io.github.mrozowski.orchestration;

public interface OrchestrationListener {

  default void beforeStep(String stepName,
                          OrchestrationContext context) {}

  default void afterStep(String stepName,
                         OrchestrationContext context,
                         StepExecutionMetadata stepMetadata) {}

  default void onFailure(String stepName,
                         Throwable exception,
                         OrchestrationContext context,
                         StepExecutionMetadata stepMetadata) {}
}
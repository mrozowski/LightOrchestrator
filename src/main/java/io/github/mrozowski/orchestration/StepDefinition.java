package io.github.mrozowski.orchestration;

import java.util.function.Function;

final class StepDefinition {

  private final String name;
  private final Key<?> key;
  private final Function<OrchestrationContext, ?> body;
  private final StepOptions options;

  StepDefinition(String name,
                 Key<?> key,
                 Function<OrchestrationContext, ?> body,
                 StepOptions options) {
    this.name = name;
    this.key = key;
    this.body = body;
    this.options = options;
  }

  String name() {
    return name;
  }

  Key<?> key() {
    return key;
  }

  Function<OrchestrationContext, ?> body() {
    return body;
  }

  StepOptions options() {
    return options;
  }
}
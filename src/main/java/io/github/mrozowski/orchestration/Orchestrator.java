package io.github.mrozowski.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Orchestrator {

  private final List<StepDefinition> steps;
  private final List<OrchestrationListener> listeners;

  private Orchestrator(List<StepDefinition> steps,
                       List<OrchestrationListener> listeners) {
    this.steps = List.copyOf(steps);
    this.listeners = List.copyOf(listeners);
  }

  public static Builder builder() {
    return new Builder();
  }

  public OrchestrationResult execute(OrchestrationContext context) {
    return OrchestrationExecutor.execute(steps, listeners, context);
  }

  public OrchestrationResult execute() {
    return execute(OrchestrationContext.empty());
  }

  // ================= Builder =================

  public static final class Builder {

    private final List<StepDefinition> steps = new ArrayList<>();
    private final List<OrchestrationListener> listeners = new ArrayList<>();

    public <T> Builder step(Key<T> key,
                            Function<OrchestrationContext, T> fn) {
      return step(key, fn, StepOptions.defaults());
    }

    public <T> Builder step(Key<T> key,
                            Function<OrchestrationContext, T> fn,
                            StepOptions options) {
      StepDefinition def = new StepDefinition(key.name(), key, fn, options);
      steps.add(def);
      return this;
    }

    public Builder step(String name, Consumer<OrchestrationContext> consumer) {
      return step(name, consumer, StepOptions.defaults());
    }

    public Builder step(String name, Consumer<OrchestrationContext> consumer, StepOptions options) {
      StepDefinition def = new StepDefinition(
          name,
          null,
          ctx -> {
            consumer.accept(ctx);
            return null;
          },
          options
      );
      steps.add(def);
      return this;
    }

    public Builder listener(OrchestrationListener listener) {
      listeners.add(listener);
      return this;
    }

    public Orchestrator build() {
      return new Orchestrator(steps, listeners);
    }
  }
}
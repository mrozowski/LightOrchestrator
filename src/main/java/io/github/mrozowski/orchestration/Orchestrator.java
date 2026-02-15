package io.github.mrozowski.orchestration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Collections.singletonList;

public final class Orchestrator {

  private final List<List<StepDefinition>> steps;
  private final List<OrchestrationListener> listeners;

  private Orchestrator(List<List<StepDefinition>> steps,
                       List<OrchestrationListener> listeners) {
    this.steps = List.copyOf(steps);
    this.listeners = List.copyOf(listeners);
  }

  public static Builder builder() {
    return new Builder();
  }

  public OrchestrationResult execute(OrchestrationContext context, ExecutorService executor) {
    return OrchestrationExecutor.execute(steps, listeners, context, executor);
  }

  public OrchestrationResult execute(OrchestrationContext context) {
    return OrchestrationExecutor.execute(steps, listeners, context, null);
  }

  public OrchestrationResult execute(ExecutorService executor) {
    return execute(OrchestrationContext.empty(), executor);
  }

  public OrchestrationResult execute() {
    return execute(OrchestrationContext.empty());
  }


  // ================= Builder =================

  public static final class Builder {

    private final List<List<StepDefinition>> steps = new ArrayList<>();
    private final List<OrchestrationListener> listeners = new ArrayList<>();

    // ---------- Function ----------
    public <T> Builder step(Key<T> key,
                            Function<OrchestrationContext, T> fn) {
      return step(key, fn, StepOptions.defaults());
    }

    public <T> Builder step(Key<T> key,
                            Function<OrchestrationContext, T> fn,
                            StepOptions options) {
      StepDefinition step = new StepDefinition(key.name(), key, fn, options);
      steps.add(singletonList(step));
      return this;
    }

    // ---------- Supplier ----------
    public <T> Builder step(Key<T> key,
                            Supplier<T> supplier) {
      return step(key, supplier, StepOptions.defaults());
    }

    public <T> Builder step(Key<T> key,
                            Supplier<T> supplier,
                            StepOptions options) {
      StepDefinition step = new StepDefinition(
          key.name(),
          key,
          ctx -> supplier.get(),
          options);
      steps.add(singletonList(step));
      return this;
    }

    // ---------- Consumer ----------
    public Builder step(String name, Consumer<OrchestrationContext> consumer) {
      return step(name, consumer, StepOptions.defaults());
    }

    public Builder step(String name, Consumer<OrchestrationContext> consumer, StepOptions options) {
      StepDefinition step = new StepDefinition(
          name,
          null,
          ctx -> {
            consumer.accept(ctx);
            return null;
          },
          options
      );
      steps.add(singletonList(step));
      return this;
    }

    // ---------- Runnable ----------
    public Builder step(String name, Runnable runnable) {
      return step(name, runnable, StepOptions.defaults());
    }

    public Builder step(String name, Runnable runnable, StepOptions options) {
      StepDefinition step = new StepDefinition(
          name,
          null,
          ctx -> {
            runnable.run();
            return null;
          },
          options
      );
      steps.add(singletonList(step));
      return this;
    }

    public Builder parallelSteps(Consumer<ParallelBuilder> consumer) {
      ParallelBuilder pb = new ParallelBuilder();
      consumer.accept(pb);

      if (pb.steps.isEmpty()) {
        throw new IllegalStateException("Parallel step group cannot be empty");
      }

      steps.add(List.copyOf(pb.steps));
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



  public static final class ParallelBuilder {

    private final List<StepDefinition> steps = new ArrayList<>();

    // ---------- Function ----------
    public <T> ParallelBuilder step(Key<T> key,
                            Function<OrchestrationContext, T> fn) {
      return step(key, fn, StepOptions.defaults());
    }

    public <T> ParallelBuilder step(Key<T> key,
                            Function<OrchestrationContext, T> fn,
                            StepOptions options) {
      StepDefinition step = new StepDefinition(key.name(), key, fn, options);
      steps.add(step);
      return this;
    }

    // ---------- Supplier ----------
    public <T> ParallelBuilder step(Key<T> key,
                            Supplier<T> supplier) {
      return step(key, supplier, StepOptions.defaults());
    }

    public <T> ParallelBuilder step(Key<T> key,
                            Supplier<T> supplier,
                            StepOptions options) {
      StepDefinition step = new StepDefinition(
          key.name(),
          key,
          ctx -> supplier.get(),
          options);
      steps.add(step);
      return this;
    }

    // ---------- Consumer ----------
    public ParallelBuilder step(String name, Consumer<OrchestrationContext> consumer) {
      return step(name, consumer, StepOptions.defaults());
    }

    public ParallelBuilder step(String name, Consumer<OrchestrationContext> consumer, StepOptions options) {
      StepDefinition step = new StepDefinition(
          name,
          null,
          ctx -> {
            consumer.accept(ctx);
            return null;
          },
          options
      );
      steps.add(step);
      return this;
    }

    // ---------- Runnable ----------
    public ParallelBuilder step(String name, Runnable runnable) {
      return step(name, runnable, StepOptions.defaults());
    }

    public ParallelBuilder step(String name, Runnable runnable, StepOptions options) {
      StepDefinition step = new StepDefinition(
          name,
          null,
          ctx -> {
            runnable.run();
            return null;
          },
          options
      );
      steps.add(step);
      return this;
    }
  }
}
package io.github.mrozowski.orchestration;

import java.util.ArrayList;
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

    /**
     * Registers a step in the orchestration.
     *
     * @param key      identifies the step and its result type
     * @param function step logic that takes {@link OrchestrationContext} and produces a value
     * @param <T>      result type of the step
     * @return this builder
     */
    public <T> Builder step(Key<T> key,
                            Function<OrchestrationContext, T> function) {
      return step(key, function, StepOptions.defaults());
    }

    /**
     * Registers a step in the orchestration.
     *
     * @param key      identifies the step and its result type
     * @param function step logic that takes {@link OrchestrationContext} and produces a value
     * @param options  provides additional step options like retry policy
     * @param <T>      result type of the step
     * @return this builder
     */
    public <T> Builder step(Key<T> key,
                            Function<OrchestrationContext, T> function,
                            StepOptions options) {
      StepDefinition step = new StepDefinition(key.name(), key, function, options);
      steps.add(singletonList(step));
      return this;
    }

    // ---------- Supplier ----------

    /**
     * Registers a step in the orchestration.
     *
     * @param key      identifies the step and its result type
     * @param supplier step logic producing a value
     * @param <T>      result type of the step
     * @return this builder
     */
    public <T> Builder step(Key<T> key,
                            Supplier<T> supplier) {
      return step(key, supplier, StepOptions.defaults());
    }

    /**
     * Registers a step in the orchestration.
     *
     * @param key      identifies the step and its result type
     * @param supplier step logic producing a value
     * @param options  provides additional step options like retry policy
     * @param <T>      result type of the step
     * @return this builder
     */
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

    /**
     * Registers a step in the orchestration.
     *
     * @param name     identifies the step name
     * @param consumer step logic that takes {@link OrchestrationContext} and doesn't produce any value
     * @return this builder
     */
    public Builder step(String name, Consumer<OrchestrationContext> consumer) {
      return step(name, consumer, StepOptions.defaults());
    }

    /**
     * Registers a step in the orchestration.
     *
     * @param name     identifies the step name
     * @param consumer step logic that takes {@link OrchestrationContext} and doesn't produce any value
     * @param options  provides additional step options like retry policy
     * @return this builder
     */
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

    /**
     * Registers a step in the orchestration.
     *
     * @param name     identifies the step name
     * @param runnable step logic
     * @return this builder
     */
    public Builder step(String name, Runnable runnable) {
      return step(name, runnable, StepOptions.defaults());
    }

    /**
     * Registers a step in the orchestration.
     *
     * @param name     identifies the step name
     * @param runnable step logic
     * @param options  provides additional step options like retry policy
     * @return this builder
     */
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

    /**
     * Registers multiple steps in the orchestration to be processed in parallel
     * <p>Example usage:</p>
     * <pre>{@code
     *     var orchestrator = Orchestrator.builder()
     *         .parallelSteps(p -> {
     *           p.step("step-1", ctx -> methodWithStepLogic());
     *           p.step("step-2", ctx -> anotherStepMethod());
     *         })
     *         .build();
     * }</pre>
     *
     * @param consumer lambda that uses ParallelBuilder to specify steps
     * @return this builder
     */
    public Builder parallelSteps(Consumer<ParallelBuilder> consumer) {
      ParallelBuilder pb = new ParallelBuilder();
      consumer.accept(pb);

      if (pb.steps.isEmpty()) {
        throw new IllegalStateException("Parallel step group cannot be empty");
      }

      steps.add(List.copyOf(pb.steps));
      return this;
    }

    /**
     * Register Orchestration listener.
     * <p>
     * It's optional listener that allows to run logic before and after each step and on step failure
     * <ul>
     *   <li>beforeStep - runs before executing step</li>
     *   <li>afterStep  - runs after successful step execution</li>
     *   <li>onFailure  - runs only when there was step failure</li>
     * </ul>
     * </p>
     * @param listener   listener that implements {@link OrchestrationListener}
     * @return this builder
     */
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
    /**
     * Registers a step in the orchestration.
     *
     * @param key      identifies the step and its result type
     * @param function step logic that takes {@link OrchestrationContext} and produces a value
     * @param <T>      result type of the step
     * @return this builder
     */
    public <T> ParallelBuilder step(Key<T> key,
                                    Function<OrchestrationContext, T> function) {
      return step(key, function, StepOptions.defaults());
    }

    /**
     * Registers a step in the orchestration.
     *
     * @param key      identifies the step and its result type
     * @param function step logic that takes {@link OrchestrationContext} and produces a value
     * @param options  provides additional step options like retry policy
     * @param <T>      result type of the step
     * @return this builder
     */
    public <T> ParallelBuilder step(Key<T> key,
                                    Function<OrchestrationContext, T> function,
                                    StepOptions options) {
      StepDefinition step = new StepDefinition(key.name(), key, function, options);
      steps.add(step);
      return this;
    }

    // ---------- Supplier ----------
    /**
     * Registers a step in the orchestration.
     *
     * @param key      identifies the step and its result type
     * @param supplier step logic producing a value
     * @param <T>      result type of the step
     * @return this builder
     */
    public <T> ParallelBuilder step(Key<T> key,
                                    Supplier<T> supplier) {
      return step(key, supplier, StepOptions.defaults());
    }

    /**
     * Registers a step in the orchestration.
     *
     * @param key      identifies the step and its result type
     * @param supplier step logic producing a value
     * @param options  provides additional step options like retry policy
     * @param <T>      result type of the step
     * @return this builder
     */
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
    /**
     * Registers a step in the orchestration.
     *
     * @param name     identifies the step name
     * @param consumer step logic that takes {@link OrchestrationContext} and doesn't produce any value
     * @return this builder
     */
    public ParallelBuilder step(String name, Consumer<OrchestrationContext> consumer) {
      return step(name, consumer, StepOptions.defaults());
    }

    /**
     * Registers a step in the orchestration.
     *
     * @param name     identifies the step name
     * @param consumer step logic that takes {@link OrchestrationContext} and doesn't produce any value
     * @param options  provides additional step options like retry policy
     * @return this builder
     */
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
    /**
     * Registers a step in the orchestration.
     *
     * @param name     identifies the step name
     * @param runnable step logic
     * @return this builder
     */
    public ParallelBuilder step(String name, Runnable runnable) {
      return step(name, runnable, StepOptions.defaults());
    }

    /**
     * Registers a step in the orchestration.
     *
     * @param name     identifies the step name
     * @param runnable step logic
     * @param options  provides additional step options like retry policy
     * @return this builder
     */
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
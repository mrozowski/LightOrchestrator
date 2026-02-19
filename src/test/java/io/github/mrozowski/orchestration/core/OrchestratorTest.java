package io.github.mrozowski.orchestration.core;

import io.github.mrozowski.orchestration.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.mrozowski.orchestration.OrchestrationResult.Status.FAILED;
import static io.github.mrozowski.orchestration.OrchestrationResult.Status.SUCCESS;
import static org.junit.jupiter.api.Assertions.*;

public class OrchestratorTest {

  @Test
  void should_execute_steps_sequentially_in_declared_order() {
    // Given
    List<String> executionOrder = new ArrayList<>();
    var orchestrator = Orchestrator.builder()
        .step("step-1", ctx -> executionOrder.add("step-1"))
        .step("step-2", ctx -> executionOrder.add("step-2"))
        .build();

    // When
    var result = orchestrator.execute();

    // Then
    assertEquals(SUCCESS, result.status());
    assertEquals(List.of("step-1", "step-2"), executionOrder);

    assertEquals(2, result.steps().size());
    assertTrue(result.steps().get(0).success());
    assertTrue(result.steps().get(1).success());

    assertEquals("step-1", result.steps().get(0).stepName());
    assertEquals("step-2", result.steps().get(1).stepName());
  }

  @Test
  void should_store_and_retrieve_typed_values_from_context_between_steps() {
    // Given
    Key<String> greetingKey = Key.of("greeting");
    Key<Integer> lengthKey = Key.of("length");

    var orchestrator = Orchestrator.builder()
        .step(greetingKey, ctx -> "hello")
        .step(lengthKey, ctx -> {
          String greeting = ctx.get(greetingKey);
          return greeting.length();
        })
        .build();

    // When
    var result = orchestrator.execute();

    // Then
    assertEquals(SUCCESS, result.status());
    assertEquals("hello", result.context().get(greetingKey));
    assertEquals(5, result.context().get(lengthKey));
  }

  @Test
  void should_retry_step_until_success_within_max_attempts() {
    // Given
    AtomicInteger counter = new AtomicInteger();
    var orchestrator = Orchestrator.builder()
        .step("retry-step", ctx -> {
              int attempt = counter.incrementAndGet();
              if (attempt < 3) {
                throw new IllegalStateException("Failure on attempt " + attempt);
              }
            },
            StepOptions.retry(3)
        )
        .build();

    // When
    var result = orchestrator.execute();

    // Then
    assertEquals(SUCCESS, result.status());

    StepExecutionMetadata execution = result.steps().getFirst();
    assertTrue(execution.success());
    assertEquals(3, execution.attempts());
    assertEquals(3, counter.get());
    assertNull(execution.exception());
  }

  @Test
  void should_stop_execution_and_mark_failed_when_step_fails_with_stop_strategy() {
    // Given
    AtomicBoolean thirdStepExecuted = new AtomicBoolean(false);

    var orchestrator = Orchestrator.builder()
        .step("step-1", ctx -> {
        })
        .step("step-2", ctx -> {
          throw new IllegalStateException("boom");
        }) // default strategy = STOP
        .step("step-3", ctx -> thirdStepExecuted.set(true))
        .build();

    // When
    var result = orchestrator.execute();

    // Then
    assertEquals(FAILED, result.status());

    assertEquals(2, result.steps().size());

    StepExecutionMetadata failedStep = result.steps().get(1);
    assertFalse(failedStep.success());
    assertNotNull(failedStep.exception());

    assertFalse(thirdStepExecuted.get());
  }

  @Test
  void should_continue_execution_and_mark_partial_when_failure_strategy_is_continue() {
    // Given
    AtomicBoolean thirdExecuted = new AtomicBoolean(false);

    var orchestrator = Orchestrator.builder()
        .step("step-1", ctx -> {})
        .step("step-2", ctx -> {
              throw new IllegalStateException("boom");
            },
            StepOptions.builder()
                .failureStrategy(FailureStrategy.CONTINUE)
                .build()
        )
        .step("step-3", ctx -> thirdExecuted.set(true))
        .build();

    // When
    var result = orchestrator.execute();

    // Then
    assertEquals(OrchestrationResult.Status.PARTIAL, result.status());
    assertEquals(3, result.steps().size());

    assertFalse(result.steps().get(1).success());
    assertTrue(thirdExecuted.get());
  }

  @Test
  void should_fail_when_retries_are_exhausted() {
    // Given
    AtomicInteger attempts = new AtomicInteger();

    var orchestrator = Orchestrator.builder()
        .step("retry-step", ctx -> {
              attempts.incrementAndGet();
              throw new IllegalStateException("fail");
            },
            StepOptions.retry(3)
        )
        .build();

    // When
    var result = orchestrator.execute();

    // Then
    assertEquals(OrchestrationResult.Status.FAILED, result.status());
    assertEquals(3, attempts.get());

    StepExecutionMetadata execution = result.steps().getFirst();
    assertEquals(3, execution.attempts());
    assertFalse(execution.success());
    assertNotNull(execution.exception());
  }

  @Test
  void should_invoke_listener_methods_in_correct_lifecycle_order() {
    // Given
    List<String> events = new ArrayList<>();
    OrchestrationListener listener = new TestListener(events);

    var orchestrator = Orchestrator.builder()
        .listener(listener)
        .step("ok", ctx -> {
        })
        .step("fail", ctx -> {
          throw new IllegalStateException();
        })
        .build();

    // When
    orchestrator.execute();

    // Then
    assertEquals(
        List.of(
            "before:ok",
            "after:ok",
            "before:fail",
            "failure:fail"
        ),
        events
    );
  }

  @Test
  void should_not_break_orchestration_when_listener_throws_exception() {
    // Given
    OrchestrationListener faultyListener = new OrchestrationListener() {
      public void beforeStep(String name, OrchestrationContext ctx) {
        throw new RuntimeException("listener failure");
      }
    };

    var orchestrator = Orchestrator.builder()
        .listener(faultyListener)
        .step("step-1", ctx -> {
        })
        .build();

    // When
    var result = orchestrator.execute();

    // Then
    assertEquals(OrchestrationResult.Status.SUCCESS, result.status());
    assertEquals(1, result.steps().size());
    assertTrue(result.steps().getFirst().success());
  }


  @Test
  void should_execute_steps_in_parallel() {
    // Given
    AtomicInteger running = new AtomicInteger(0);
    AtomicInteger maxRunning = new AtomicInteger(0);
    CyclicBarrier barrier = new CyclicBarrier(2);

    var orchestrator = Orchestrator.builder()
//        .step("step-1", ctx -> {stepHelper(running, maxRunning, barrier);})
//        .step("step-2", ctx -> {stepHelper(running, maxRunning, barrier);})
        .parallelSteps(p -> {
          p.step("step-1", ctx -> stepHelper(running, maxRunning, barrier));
          p.step("step-2", ctx -> stepHelper(running, maxRunning, barrier));
        })
        .build();

    // When
    var result = orchestrator.execute();

    // Then
    assertEquals(SUCCESS, result.status());
    assertEquals(2, result.steps().size());
    assertTrue(result.steps().get(0).success());
    assertTrue(result.steps().get(1).success());

    // If sequential, test will deadlock at barrier and fail after 1s
    assertTrue(maxRunning.get() > 1);
  }

  @Test
  void should_invoke_listener_when_running_parallel_steps() {
    // Given
    List<String> events = Collections.synchronizedList(new ArrayList<>());
    OrchestrationListener listener = new TestListener(events);

    var orchestrator = Orchestrator.builder()
        .listener(listener)
        .parallelSteps(p -> {
          p.step("ok", ctx -> {
          });
          p.step("fail", ctx -> {
            throw new IllegalStateException();
          });
        })
        .build();

    // When
    orchestrator.execute();

    // Then
    assertEquals(4, events.size());

    assertTrue(events.contains("before:ok"));
    assertTrue(events.contains("after:ok"));
    assertTrue(events.contains("before:fail"));
    assertTrue(events.contains("failure:fail"));
  }

  private static void stepHelper(AtomicInteger running, AtomicInteger maxRunning, CyclicBarrier barrier) {
    int current = running.incrementAndGet();
    maxRunning.updateAndGet(prev -> Math.max(prev, current));

    try {
      barrier.await(1, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    running.decrementAndGet();
  }

  private static class TestListener implements OrchestrationListener {
    private final List<String> events;

    TestListener(List<String> events) {
      this.events = events;
    }

    @Override
    public void beforeStep(String name, OrchestrationContext ctx) {
      events.add("before:" + name);
    }

    @Override
    public void afterStep(String name, OrchestrationContext ctx, StepExecutionMetadata metadata) {
      events.add("after:" + name);
    }

    @Override
    public void onFailure(String name, Throwable ex, OrchestrationContext ctx, StepExecutionMetadata metadata) {
      events.add("failure:" + name);
    }
  }
}
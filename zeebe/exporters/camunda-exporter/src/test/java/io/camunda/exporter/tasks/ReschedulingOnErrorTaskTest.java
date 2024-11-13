/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.exporter.tasks.archiver.ArchiverJob;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoCloseResources
final class ReschedulingOnErrorTaskTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReschedulingOnErrorTaskTest.class);

  @AutoCloseResource
  private final ScheduledThreadPoolExecutor executor =
      Mockito.spy(new ScheduledThreadPoolExecutor(1));

  @Test
  void shouldRescheduleTaskOnError() {
    // given
    final var task = new RescheduledOnErrorTask(new FailingJob(), 10, executor, LOGGER);

    // when
    task.run();

    // then
    final var inOrder = Mockito.inOrder(executor);
    inOrder
        .verify(executor, Mockito.timeout(5_000).times(1))
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldRescheduleTaskOnErrorWithDelay() {
    // given
    final var task = new RescheduledOnErrorTask(new FailingJob(), 10, executor, LOGGER);

    // when
    task.run();

    // then
    final var inOrder = Mockito.inOrder(executor);
    inOrder
        .verify(executor, Mockito.timeout(5_000).times(1))
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
    inOrder
        .verify(executor, Mockito.timeout(5_000).times(1))
        .schedule(task, 12L, TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldNotRescheduleOnSuccessfulJob() {
    // given
    final var job =
        new ArchiverJob() {
          @Override
          public CompletableFuture<Integer> getNextJob() {
            return CompletableFuture.completedFuture(1);
          }
        };
    final var task = new RescheduledOnErrorTask(job, 10, executor, LOGGER);

    // when
    task.run();

    // then
    assertTrue(executor.getQueue().isEmpty(), "No tasks should be scheduled");
    Awaitility.await("No tasks should be active")
        .untilAsserted(() -> assertEquals(0, executor.getActiveCount()));
  }

  private static final class FailingJob implements ArchiverJob {
    @Override
    public CompletableFuture<Integer> getNextJob() {
      return CompletableFuture.failedFuture(new RuntimeException("failure"));
    }
  }
}

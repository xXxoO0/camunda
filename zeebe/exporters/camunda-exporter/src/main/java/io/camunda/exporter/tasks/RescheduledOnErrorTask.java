/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks;

import io.camunda.zeebe.util.ExponentialBackoff;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

public final class RescheduledOnErrorTask implements Runnable {
  private final BackgroundTask task;
  private final ScheduledExecutorService executor;
  private final Logger logger;
  private final ExponentialBackoff errorStrategy;

  private long errorDelayMs;

  public RescheduledOnErrorTask(
      final BackgroundTask task,
      final long delayBetweenRunsMs,
      final ScheduledExecutorService executor,
      final Logger logger) {
    this.task = task;
    this.executor = executor;
    this.logger = logger;

    errorStrategy = new ExponentialBackoff(10_000, delayBetweenRunsMs, 1.2, 0);
  }

  @Override
  public void run() {
    var result = task.execute();
    // while we could always expect this to return a non-null result, we don't necessarily want to
    // stop, and more importantly, we want to make it transparent that something went wrong
    if (result == null) {
      logger.warn(
          "Expected to perform a background task, but no result returned for job {}; rescheduling anyway",
          task);
      result = CompletableFuture.completedFuture(0);
    }

    result
        .thenApplyAsync(
            ignore -> {
              logger.trace("Task {} completed successfully.", task);
              return null;
            })
        .exceptionallyAsync(
            error -> {
              errorDelayMs = errorStrategy.applyAsLong(errorDelayMs);
              logger.error(
                  "Error occurred while performing a background task; operation will be retried",
                  error);
              logger.trace("Rescheduling failed task {} in {}ms", task, errorDelayMs);
              executor.schedule(this, errorDelayMs, TimeUnit.MILLISECONDS);
              return null;
            },
            executor);
  }
}

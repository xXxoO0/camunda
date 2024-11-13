/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class ApplyRolloverPeriodJob implements ArchiverJob {

  private final ArchiverRepository repository;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;
  private final Executor executor;

  public ApplyRolloverPeriodJob(
      final ArchiverRepository repository,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    this.repository = repository;
    this.metrics = metrics;
    this.logger = logger;
    this.executor = executor;
  }

  @Override
  public CompletableFuture<Integer> getNextJob() {
    return null;
  }
}

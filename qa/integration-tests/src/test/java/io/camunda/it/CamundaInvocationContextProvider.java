/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static java.util.Arrays.asList;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;

public class CamundaInvocationContextProvider implements TestTemplateInvocationContextProvider,
    AfterAllCallback, BeforeAllCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      CamundaInvocationContextProvider.class);

  private final Map<String, TestStandaloneCamunda> testStandaloneCamundaMap;

  public CamundaInvocationContextProvider() {
    testStandaloneCamundaMap = new HashMap<>();
    testStandaloneCamundaMap.put("camundaWithPostgresql", TestStandaloneCamunda
        .withRdbms()
        .withDatabaseContainer(new PostgreSQLContainer<>("postgres:16-alpine")));
    testStandaloneCamundaMap.put("camundaWithMariadb", TestStandaloneCamunda
        .withRdbms()
        .withDatabaseContainer(new MariaDBContainer<>("mariadb:11.4")));
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    LOGGER.info("Starting up '{}' camunda instances ...", testStandaloneCamundaMap.size());
    testStandaloneCamundaMap.entrySet().parallelStream().forEach(entry -> {
      LOGGER.info("Start up '{}'...", entry.getKey());
      final TestStandaloneCamunda testStandaloneCamunda = entry.getValue();
      if (testStandaloneCamunda.databaseContainer() instanceof final JdbcDatabaseContainer<?> jdbcDatabaseContainer) {
        jdbcDatabaseContainer.start();
        testStandaloneCamunda
            .withProperty("spring.datasource.url", jdbcDatabaseContainer.getJdbcUrl())
            .withProperty("spring.datasource.username", jdbcDatabaseContainer.getUsername())
            .withProperty("spring.datasource.password", jdbcDatabaseContainer.getPassword())
            .withProperty("spring.datasource.password", jdbcDatabaseContainer.getPassword())
            .start();
      } else {
        testStandaloneCamunda.start();
      }

      // TODO what we need here?
      testStandaloneCamunda.await(TestHealthProbe.STARTED);
      testStandaloneCamunda.await(TestHealthProbe.READY);
      testStandaloneCamunda.awaitCompleteTopology();
      LOGGER.info("Start up of '{}' finished.", entry.getKey());
    });
  }

  @Override
  public boolean supportsTestTemplate(final ExtensionContext extensionContext) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext extensionContext) {
    return testStandaloneCamundaMap.keySet().stream().map(this::invocationContext);
  }

  private TestTemplateInvocationContext invocationContext(final String standaloneCamundaKey) {
    return new TestTemplateInvocationContext() {

      @Override
      public String getDisplayName(final int invocationIndex) {
        return standaloneCamundaKey;
      }

      @Override
      public List<Extension> getAdditionalExtensions() {
        return asList(
            (BeforeEachCallback) context -> {
              //If we reset the exporter, we also have to find a way to reset the rdbms since we store the last exported position there
              // RecordingExporter.reset();
            },
            new ParameterResolver() {

              @Override
              public boolean supportsParameter(final ParameterContext parameterCtx,
                  final ExtensionContext extensionCtx) {
                return parameterCtx.getParameter().getType().equals(TestStandaloneCamunda.class);
              }

              @Override
              public Object resolveParameter(final ParameterContext parameterCtx,
                  final ExtensionContext extensionCtx) {
                return testStandaloneCamundaMap.get(standaloneCamundaKey);
              }
            });
      }
    };
  }

  @Override
  public void afterAll(final ExtensionContext context) {
    testStandaloneCamundaMap.entrySet().parallelStream().forEach(entry -> {
      LOGGER.info("Shut down '{}'...", entry.getKey());
      final TestStandaloneCamunda testStandaloneCamunda = entry.getValue();
      testStandaloneCamunda.stop();
    });
  }
}

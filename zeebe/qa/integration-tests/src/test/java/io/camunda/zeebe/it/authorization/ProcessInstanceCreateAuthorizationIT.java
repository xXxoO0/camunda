/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import static io.camunda.zeebe.it.util.AuthorizationsUtil.createClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.Profile;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@AutoCloseResources
@Testcontainers
@ZeebeIntegration
public class ProcessInstanceCreateAuthorizationIT {
  private static final DockerImageName ELASTIC_IMAGE =
      DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
          .withTag(RestClient.class.getPackage().getImplementationVersion());

  @Container
  private static final ElasticsearchContainer CONTAINER =
      new ElasticsearchContainer(ELASTIC_IMAGE)
          // use JVM option files to avoid overwriting default options set by the ES container class
          .withClasspathResourceMapping(
              "elasticsearch-fast-startup.options",
              "/usr/share/elasticsearch/config/jvm.options.d/ elasticsearch-fast-startup.options",
              BindMode.READ_ONLY)
          // can be slow in CI
          .withStartupTimeout(Duration.ofMinutes(5))
          .withEnv("action.auto_create_index", "true")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("xpack.watcher.enabled", "false")
          .withEnv("xpack.ml.enabled", "false")
          .withEnv("action.destructive_requires_name", "false");

  private static final String PROCESS_ID = "processId";

  @TestZeebe(autoStart = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withBrokerConfig(
              b -> b.getExperimental().getEngine().getAuthorizations().setEnableAuthorization(true))
          .withAdditionalProfile(Profile.AUTH_BASIC);

  private static AuthorizationsUtil authUtil;
  private static ZeebeClient defaultUserClient;

  @BeforeAll
  static void beforeAll() {
    BROKER.withCamundaExporter("http://" + CONTAINER.getHttpHostAddress());
    BROKER.start();

    final var defaultUsername = "demo";
    defaultUserClient = createClient(BROKER, defaultUsername, "demo");
    authUtil = new AuthorizationsUtil(BROKER, defaultUserClient, CONTAINER.getHttpHostAddress());

    authUtil.awaitUserExistsInElasticsearch(defaultUsername);
    defaultUserClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done(), "process.xml")
        .send()
        .join();
  }

  @Test
  void shouldBeAuthorizedToCreateInstanceWithDefaultUser() {
    // when then
    final var processInstanceEvent =
        defaultUserClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();
    assertThat(processInstanceEvent.getBpmnProcessId()).isEqualTo(PROCESS_ID);
  }

  @Test
  void shouldBeAuthorizedToCreateInstanceWithResultWithDefaultUser() {
    // when then
    final var processInstanceResult =
        defaultUserClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .withResult()
            .send()
            .join();
    assertThat(processInstanceResult.getBpmnProcessId()).isEqualTo(PROCESS_ID);
  }

  @Test
  void shouldBeAuthorizedToCreateInstanceWithUser() {
    // given
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION, PermissionTypeEnum.CREATE, List.of(PROCESS_ID)));

    try (final var client = authUtil.createClient(username, password)) {
      // when then
      final var processInstanceEvent =
          client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();
      assertThat(processInstanceEvent.getBpmnProcessId()).isEqualTo(PROCESS_ID);
    }
  }

  @Test
  void shouldBeAuthorizedToCreateInstanceWithResultWithUser() {
    // given
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION, PermissionTypeEnum.CREATE, List.of(PROCESS_ID)));

    try (final var client = authUtil.createClient(username, password)) {
      // when then
      final var processInstanceResult =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(PROCESS_ID)
              .latestVersion()
              .withResult()
              .send()
              .join();
      assertThat(processInstanceResult.getBpmnProcessId()).isEqualTo(PROCESS_ID);
    }
  }

  @Test
  void shouldBeUnauthorizedToCreateInstanceIfNoPermissions() {
    // given
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var createFuture =
          client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send();

      // then
      assertThatThrownBy(createFuture::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: UNAUTHORIZED")
          .hasMessageContaining("status: 401")
          .hasMessageContaining(
              "Unauthorized to perform operation 'CREATE' on resource 'PROCESS_DEFINITION'");
    }
  }

  @Test
  void shouldBeUnauthorizedToCreateInstanceWithResultIfNoPermissions() {
    // given
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var createFuture =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(PROCESS_ID)
              .latestVersion()
              .withResult()
              .send();

      // then
      assertThatThrownBy(createFuture::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: UNAUTHORIZED")
          .hasMessageContaining("status: 401")
          .hasMessageContaining(
              "Unauthorized to perform operation 'CREATE' on resource 'PROCESS_DEFINITION'");
    }
  }
}
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ZeebeIntegration
@ExtendWith(CamundaInvocationContextProvider.class)
public class StandaloneCamundaWithRdbmsAndTestTemplatesTest {

  @TestTemplate
  public void shouldCreateAndRetrieveInstance(final TestStandaloneCamunda testStandaloneCamunda) {
    // given
    final var zeebeClient = testStandaloneCamunda.zeebeClient();

    // when
    zeebeClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("test")
                .zeebeJobType("type")
                .endEvent()
                .done(),
            "simple.bpmn")
        .send()
        .join();

    final var processInstanceEvent =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // then
    final var testClient = testStandaloneCamunda.newRestV2ApiClient();
    Awaitility.await(
            "should receive data from " + testStandaloneCamunda.databaseContainer().getContainerName())
        .pollInterval(Duration.ofSeconds(1))
        .timeout(Duration.ofMinutes(1))
        .untilAsserted(
            () -> {
              final Either<Exception, ProcessInstanceSearchQueryResponse> eitherProcessInstanceResult =
                  testClient.getProcessInstanceWith(processInstanceEvent.getProcessInstanceKey());

              if (eitherProcessInstanceResult.isLeft()) {
                System.out.println(eitherProcessInstanceResult.getLeft().getMessage());
              }

              // has no exception
              assertThat(eitherProcessInstanceResult.isRight())
                  .withFailMessage("Expect no error on retrieving process instance")
                  .isTrue();

              final var processInstanceResult = eitherProcessInstanceResult.get();

              final long total = processInstanceResult.getPage().getTotalItems();
              assertThat(total)
                  .withFailMessage("Expect to read a process instance from RDBMS")
                  .isGreaterThan(0);

              assertThat(processInstanceResult.getItems().getFirst().getProcessInstanceKey())
                  .withFailMessage("Expect to read the expected process instance from RDBMS")
                  .isEqualTo(processInstanceEvent.getProcessInstanceKey());
            });
  }

  @TestTemplate
  public void shouldCreateAndRetrieveAnotherInstance(
      final TestStandaloneCamunda testStandaloneCamunda) {
    // given
    final var zeebeClient = testStandaloneCamunda.zeebeClient();

    // when
    zeebeClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process2")
                .startEvent()
                .serviceTask("test2")
                .zeebeJobType("type2")
                .endEvent()
                .done(),
            "simple2.bpmn")
        .send()
        .join();

    final var processInstanceEvent =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process2")
            .latestVersion()
            .send()
            .join();

    // then
    final var testClient = testStandaloneCamunda.newRestV2ApiClient();
    Awaitility.await(
            "Should receive data from " + testStandaloneCamunda.databaseContainer().getContainerName())
        .pollInterval(Duration.ofSeconds(1))
        .timeout(Duration.ofMinutes(1))
        .untilAsserted(
            () -> {
              final Either<Exception, ProcessInstanceSearchQueryResponse> eitherProcessInstanceResult =
                  testClient.getProcessInstanceWith(processInstanceEvent.getProcessInstanceKey());

              if (eitherProcessInstanceResult.isLeft()) {
                System.out.println(eitherProcessInstanceResult.getLeft().getMessage());
              }

              // has no exception
              assertThat(eitherProcessInstanceResult.isRight())
                  .withFailMessage("Expect no error on retrieving process instance")
                  .isTrue();

              final var processInstanceResult = eitherProcessInstanceResult.get();

              final long total = processInstanceResult.getPage().getTotalItems();
              assertThat(total)
                  .withFailMessage("Expect to read a process instance from RDBMS")
                  .isGreaterThan(0);

              assertThat(processInstanceResult.getItems().getFirst().getProcessInstanceKey())
                  .withFailMessage("Expect to read the expected process instance from RDBMS")
                  .isEqualTo(processInstanceEvent.getProcessInstanceKey());
            });
  }
}

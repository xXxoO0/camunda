/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.impl.search.response.ProcessInstanceSequenceFlowImpl;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class ProcessInstanceSequenceFlowsTest {

  public static final List<String> RESOURCES =
      List.of(
          "process/incident_process_v1.bpmn",
          "process/bpmn_subprocess_case.bpmn",
          "process/bpm_variable_test.bpmn",
          "process/multi_instance_subprocess.bpmn");
  public static final List<Process> PROCESSES = new ArrayList<>();
  public static final List<ProcessInstanceEvent> INSTANCES = new ArrayList<>();
  private static CamundaClient camundaClient;

  @BeforeAll
  public static void setup() {
    PROCESSES.addAll(
        RESOURCES.stream()
            .map(
                resource ->
                    TestHelper.deployResource(camundaClient, resource).getProcesses().getFirst())
            .toList());
    waitForProcessesToBeDeployed(camundaClient, PROCESSES.size());

    INSTANCES.add(TestHelper.startProcessInstance(camundaClient, "incident_process_v1"));
    INSTANCES.add(TestHelper.startProcessInstance(camundaClient, "processWithSubProcess"));
    INSTANCES.add(TestHelper.startProcessInstance(camundaClient, "bpmProcessVariable"));
    INSTANCES.add(
        TestHelper.startProcessInstance(
            camundaClient,
            "multi_instance_subprocess",
            "{\"variables\": [\"foo\", \"bar\"], \"foo\": 1}"));
    INSTANCES.add(
        TestHelper.startProcessInstance(
            camundaClient,
            "multi_instance_subprocess",
            "{\"variables\": [\"foo\", \"bar\"], \"foo\": 2}"));
    TestHelper.waitForProcessInstancesToStart(camundaClient, INSTANCES.size());
  }

  private void waitForSequenceFlows(
      final CamundaClient camundaClient,
      final long processInstanceKey,
      final int expectedSequenceFlows) {
    Awaitility.await("should start process instances and import in Operate")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSequenceFlowsRequest(processInstanceKey)
                      .send()
                      .join();
              assertThat(result).hasSize(expectedSequenceFlows);
            });
  }

  @Test
  void shouldReturnOneSequenceFlow() {
    // given
    final var processDefinitionKey = PROCESSES.get(0).getProcessDefinitionKey();
    final var processInstanceKey = INSTANCES.get(0).getProcessInstanceKey();

    // when
    final var actual =
        camundaClient.newProcessInstanceSequenceFlowsRequest(processInstanceKey).send().join();

    // then
    assertThat(actual).hasSize(1);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "incident_process_v1",
                "sequenceFlow1",
                "<default>"));
  }

  @Test
  void shouldReturnTwoSequenceFlows() {
    // given
    final var processDefinitionKey = PROCESSES.get(1).getProcessDefinitionKey();
    final var processInstanceKey = INSTANCES.get(1).getProcessInstanceKey();

    // when
    final var actual =
        camundaClient.newProcessInstanceSequenceFlowsRequest(processInstanceKey).send().join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "processWithSubProcess",
                "Flow_00yb6c4",
                "<default>"),
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "processWithSubProcess",
                "Flow_14vfmwp",
                "<default>"));
  }

  @Test
  void shouldReturnThreeSequenceFlows() {
    // given
    final var processDefinitionKey = PROCESSES.get(2).getProcessDefinitionKey();
    final var processInstanceKey = INSTANCES.get(2).getProcessInstanceKey();

    // when
    final var actual =
        camundaClient.newProcessInstanceSequenceFlowsRequest(processInstanceKey).send().join();

    // then
    assertThat(actual).hasSize(3);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "bpmProcessVariable",
                "Flow_1xuspnu",
                "<default>"),
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "bpmProcessVariable",
                "Flow_03xh6j8",
                "<default>"),
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "bpmProcessVariable",
                "Flow_1ffbwjw",
                "<default>"));
  }

  @Test
  void shouldReturnMultiInstanceSequenceFlows() {
    // given
    final var processDefinitionKey = PROCESSES.get(3).getProcessDefinitionKey();
    final var processInstanceKey = INSTANCES.get(3).getProcessInstanceKey();
    waitForSequenceFlows(camundaClient, processInstanceKey, 5);

    // when
    final var actual =
        camundaClient.newProcessInstanceSequenceFlowsRequest(processInstanceKey).send().join();

    // then
    assertThat(actual).hasSize(5);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "multi_instance_subprocess",
                "Flow_01h019c",
                "<default>"),
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "multi_instance_subprocess",
                "Flow_08mqnul",
                "<default>"),
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "multi_instance_subprocess",
                "Flow_1h48f0w",
                "<default>"),
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "multi_instance_subprocess",
                "Flow_1gqyehp",
                "<default>"),
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "multi_instance_subprocess",
                "Flow_1rlbq9a",
                "<default>"));
  }

  @Test
  void shouldReturnMultiInstanceSequenceFlows2() {
    // given
    final var processDefinitionKey = PROCESSES.get(3).getProcessDefinitionKey();
    final var processInstanceKey = INSTANCES.get(4).getProcessInstanceKey();
    waitForSequenceFlows(camundaClient, processInstanceKey, 5);

    // when
    final var actual =
        camundaClient.newProcessInstanceSequenceFlowsRequest(processInstanceKey).send().join();

    // then
    assertThat(actual).hasSize(5);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "multi_instance_subprocess",
                "Flow_01h019c",
                "<default>"),
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "multi_instance_subprocess",
                "Flow_08mqnul",
                "<default>"),
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "multi_instance_subprocess",
                "Flow_1h48f0w",
                "<default>"),
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "multi_instance_subprocess",
                "Flow_1hb4p0z",
                "<default>"),
            new ProcessInstanceSequenceFlowImpl(
                String.valueOf(processInstanceKey),
                String.valueOf(processDefinitionKey),
                "multi_instance_subprocess",
                "Flow_1rlbq9a",
                "<default>"));
  }
}

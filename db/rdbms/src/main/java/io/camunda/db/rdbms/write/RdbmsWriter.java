/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.service.DecisionDefinitionWriter;
import io.camunda.db.rdbms.write.service.DecisionInstanceWriter;
import io.camunda.db.rdbms.write.service.DecisionRequirementsWriter;
import io.camunda.db.rdbms.write.service.ExporterPositionService;
import io.camunda.db.rdbms.write.service.FlowNodeInstanceWriter;
import io.camunda.db.rdbms.write.service.ProcessDefinitionWriter;
import io.camunda.db.rdbms.write.service.ProcessInstanceWriter;
import io.camunda.db.rdbms.write.service.VariableWriter;

public class RdbmsWriter {

  private final ExecutionQueue executionQueue;
  private final DecisionDefinitionWriter decisionDefinitionWriter;
  private final DecisionInstanceWriter decisionInstanceWriter;
  private final DecisionRequirementsWriter decisionRequirementsWriter;
  private final ExporterPositionService exporterPositionService;
  private final FlowNodeInstanceWriter flowNodeInstanceWriter;
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ProcessInstanceWriter processInstanceWriter;
  private final VariableWriter variableWriter;

  public RdbmsWriter(
      final ExecutionQueue executionQueue, final ExporterPositionService exporterPositionService) {
    this.executionQueue = executionQueue;
    this.exporterPositionService = exporterPositionService;
    decisionDefinitionWriter = new DecisionDefinitionWriter(executionQueue);
    decisionInstanceWriter = new DecisionInstanceWriter(executionQueue);
    decisionRequirementsWriter = new DecisionRequirementsWriter(executionQueue);
    flowNodeInstanceWriter = new FlowNodeInstanceWriter(executionQueue);
    processDefinitionWriter = new ProcessDefinitionWriter(executionQueue);
    processInstanceWriter = new ProcessInstanceWriter(executionQueue);
    variableWriter = new VariableWriter(executionQueue);
  }

  public DecisionDefinitionWriter getDecisionDefinitionWriter() {
    return decisionDefinitionWriter;
  }

  public DecisionInstanceWriter getDecisionInstanceWriter() {
    return decisionInstanceWriter;
  }

  public DecisionRequirementsWriter getDecisionRequirementsWriter() {
    return decisionRequirementsWriter;
  }

  public FlowNodeInstanceWriter getFlowNodeInstanceWriter() {
    return flowNodeInstanceWriter;
  }

  public ProcessDefinitionWriter getProcessDefinitionWriter() {
    return processDefinitionWriter;
  }

  public ProcessInstanceWriter getProcessInstanceWriter() {
    return processInstanceWriter;
  }

  public VariableWriter getVariableWriter() {
    return variableWriter;
  }

  public ExporterPositionService getExporterPositionService() {
    return exporterPositionService;
  }

  public ExecutionQueue getExecutionQueue() {
    return executionQueue;
  }

  public void flush() {
    executionQueue.flush();
  }
}

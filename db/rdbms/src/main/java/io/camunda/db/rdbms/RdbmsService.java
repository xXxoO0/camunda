/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.read.service.DecisionDefinitionReader;
import io.camunda.db.rdbms.read.service.DecisionInstanceReader;
import io.camunda.db.rdbms.read.service.DecisionRequirementsReader;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader;
import io.camunda.db.rdbms.read.service.VariableReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;

/** A holder for all rdbms services */
public class RdbmsService {

  private final RdbmsWriterFactory rdbmsWriterFactory;
  private final DecisionDefinitionReader decisionDefinitionReader;
  private final DecisionInstanceReader decisionInstanceReader;
  private final DecisionRequirementsReader decisionRequirementsReader;
  private final FlowNodeInstanceReader flowNodeInstanceReader;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessInstanceReader processInstanceReader;
  private final VariableReader variableReader;

  public RdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory,
      final DecisionDefinitionReader decisionDefinitionReader,
      final DecisionInstanceReader decisionInstanceReader,
      final DecisionRequirementsReader decisionRequirementsReader,
      final FlowNodeInstanceReader flowNodeInstanceReader,
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessInstanceReader processInstanceReader,
      final VariableReader variableReader) {
    this.rdbmsWriterFactory = rdbmsWriterFactory;
    this.decisionRequirementsReader = decisionRequirementsReader;
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.decisionInstanceReader = decisionInstanceReader;
    this.flowNodeInstanceReader = flowNodeInstanceReader;
    this.processDefinitionReader = processDefinitionReader;
    this.processInstanceReader = processInstanceReader;
    this.variableReader = variableReader;
  }

  public DecisionDefinitionReader getDecisionDefinitionReader() {
    return decisionDefinitionReader;
  }

  public DecisionInstanceReader getDecisionInstanceReader() {
    return decisionInstanceReader;
  }

  public DecisionRequirementsReader getDecisionRequirementsReader() {
    return decisionRequirementsReader;
  }

  public FlowNodeInstanceReader getFlowNodeInstanceReader() {
    return flowNodeInstanceReader;
  }

  public ProcessDefinitionReader getProcessDefinitionReader() {
    return processDefinitionReader;
  }

  public ProcessInstanceReader getProcessInstanceReader() {
    return processInstanceReader;
  }

  public VariableReader getVariableReader() {
    return variableReader;
  }

  public RdbmsWriter createWriter(final long partitionId) {
    return rdbmsWriterFactory.createWriter(partitionId);
  }
}

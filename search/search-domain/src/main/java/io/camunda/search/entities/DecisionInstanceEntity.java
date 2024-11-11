/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.List;

public record DecisionInstanceEntity(
    Long key,
    DecisionInstanceState state,
    OffsetDateTime evaluationDate,
    String evaluationFailure,
    Long processDefinitionKey,
    Long processInstanceKey,
    String bpmnProcessId,
    String decisionId,
    String decisionDefinitionId,
    String decisionName,
    Integer decisionVersion,
    DecisionDefinitionType decisionType,
    String result,
    List<DecisionInstanceInputEntity> evaluatedInputs,
    List<DecisionInstanceOutputEntity> evaluatedOutputs) {

  public Builder toBuilder() {
    return new Builder()
        .key(key)
        .state(state)
        .evaluationDate(evaluationDate)
        .evaluationFailure(evaluationFailure)
        .processDefinitionKey(processDefinitionKey)
        .processInstanceKey(processInstanceKey)
        .bpmnProcessId(bpmnProcessId)
        .decisionId(decisionId)
        .decisionDefinitionId(decisionDefinitionId)
        .decisionName(decisionName)
        .decisionVersion(decisionVersion)
        .decisionType(decisionType)
        .result(result)
        .evaluatedInputs(evaluatedInputs)
        .evaluatedOutputs(evaluatedOutputs);
  }

  public static class Builder implements ObjectBuilder<DecisionInstanceEntity> {

    private Long key;
    private DecisionInstanceState state;
    private OffsetDateTime evaluationDate;
    private String evaluationFailure;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private String bpmnProcessId;
    private String decisionId;
    private String decisionDefinitionId;
    private String decisionName;
    private Integer decisionVersion;
    private DecisionDefinitionType decisionType;
    private String result;
    private List<DecisionInstanceInputEntity> evaluatedInputs;
    private List<DecisionInstanceOutputEntity> evaluatedOutputs;

    public Builder key(Long key) {
      this.key = key;
      return this;
    }

    public Builder state(DecisionInstanceState state) {
      this.state = state;
      return this;
    }

    public Builder evaluationDate(OffsetDateTime evaluationDate) {
      this.evaluationDate = evaluationDate;
      return this;
    }

    public Builder evaluationFailure(String evaluationFailure) {
      this.evaluationFailure = evaluationFailure;
      return this;
    }

    public Builder processDefinitionKey(Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processInstanceKey(Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder bpmnProcessId(String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId;
      return this;
    }

    public Builder decisionId(String decisionId) {
      this.decisionId = decisionId;
      return this;
    }

    public Builder decisionDefinitionId(String decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
      return this;
    }

    public Builder decisionName(String decisionName) {
      this.decisionName = decisionName;
      return this;
    }

    public Builder decisionVersion(Integer decisionVersion) {
      this.decisionVersion = decisionVersion;
      return this;
    }

    public Builder decisionType(DecisionDefinitionType decisionType) {
      this.decisionType = decisionType;
      return this;
    }

    public Builder result(String result) {
      this.result = result;
      return this;
    }

    public Builder evaluatedInputs(List<DecisionInstanceInputEntity> evaluatedInputs) {
      this.evaluatedInputs = evaluatedInputs;
      return this;
    }

    public Builder evaluatedOutputs(List<DecisionInstanceOutputEntity> evaluatedOutputs) {
      this.evaluatedOutputs = evaluatedOutputs;
      return this;
    }

    @Override
    public DecisionInstanceEntity build() {
      return new DecisionInstanceEntity(
          key,
          state,
          evaluationDate,
          evaluationFailure,
          processDefinitionKey,
          processInstanceKey,
          bpmnProcessId,
          decisionId,
          decisionDefinitionId,
          decisionName,
          decisionVersion,
          decisionType,
          result,
          evaluatedInputs,
          evaluatedOutputs);
    }
  }

  public record DecisionInstanceInputEntity(String id, String name, String value) {}

  public record DecisionInstanceOutputEntity(
      String id, String name, String value, String ruleId, int ruleIndex) {}

  public enum DecisionDefinitionType {
    DECISION_TABLE,
    LITERAL_EXPRESSION,
    UNSPECIFIED,
    UNKNOWN;

    public static DecisionDefinitionType fromValue(final String value) {
      for (final DecisionDefinitionType b : DecisionDefinitionType.values()) {
        if (b.name().equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  public enum DecisionInstanceState {
    EVALUATED,
    FAILED,
    UNKNOWN,
    UNSPECIFIED;

    public static DecisionInstanceState fromValue(final String value) {
      for (final DecisionInstanceState b : DecisionInstanceState.values()) {
        if (b.name().equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }
}

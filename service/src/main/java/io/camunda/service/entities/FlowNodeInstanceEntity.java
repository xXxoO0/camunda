/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowNodeInstanceEntity(
    Long key,
    Long processInstanceKey,
    Long processDefinitionKey,
    String startDate,
    String endDate,
    String flowNodeId,
    String flowNodeName,
    String treePath,
    String type,
    String state,
    Boolean incident,
    Long incidentKey,
    Long scopeKey,
    String bpmnProcessId,
    String tenantId) {}
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.SequenceFlowEntity;

public class SequenceFlowEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.SequenceFlowEntity, SequenceFlowEntity> {

  @Override
  public SequenceFlowEntity apply(
      final io.camunda.webapps.schema.entities.SequenceFlowEntity value) {
    return new SequenceFlowEntity(
        value.getId(),
        value.getActivityId(),
        value.getProcessInstanceKey(),
        value.getProcessDefinitionKey(),
        value.getBpmnProcessId(),
        value.getTenantId());
  }
}

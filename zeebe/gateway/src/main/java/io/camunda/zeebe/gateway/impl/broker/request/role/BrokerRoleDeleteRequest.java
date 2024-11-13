/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request.role;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import org.agrona.DirectBuffer;

public class BrokerRoleDeleteRequest extends BrokerExecuteCommand<RoleRecord> {
  private final RoleRecord requestDto = new RoleRecord();

  public BrokerRoleDeleteRequest() {
    super(ValueType.ROLE, RoleIntent.DELETE);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public BrokerRoleDeleteRequest setRoleKey(final long roleKey) {
    requestDto.setRoleKey(roleKey);
    return this;
  }

  @Override
  public RoleRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected RoleRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new RoleRecord();
    response.wrap(buffer);
    return response;
  }
}

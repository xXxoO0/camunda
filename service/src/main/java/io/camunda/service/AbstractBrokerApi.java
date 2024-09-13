/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public abstract class AbstractBrokerApi {

  protected final BrokerClient brokerClient;

  protected AbstractBrokerApi(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
  }

  protected <R> CompletableFuture<R> sendBrokerRequest(final BrokerRequest<R> brokerRequest) {
    return sendBrokerRequest(brokerRequest, null);
  }

  protected <R> CompletableFuture<R> sendBrokerRequest(final BrokerRequest<R> brokerRequest,
      final Authentication authentication) {
    return sendBrokerRequestWithFullResponse(brokerRequest, authentication).thenApply(
        BrokerResponse::getResponse);
  }

  protected <R> CompletableFuture<BrokerResponse<R>> sendBrokerRequestWithFullResponse(
      final BrokerRequest<R> brokerRequest,
      final Authentication authentication) {
    brokerRequest.setAuthorization(authentication.token());
    return brokerClient
        .sendRequest(brokerRequest)
        .handleAsync(
            (response, error) -> {
              if (error != null) {
                throw new CamundaServiceException(error);
              }
              if (response.isError()) {
                throw new CamundaServiceException(response.getError());
              }
              if (response.isRejection()) {
                throw new CamundaServiceException(response.getRejection());
              }
              return response;
            });
  }

  protected DirectBuffer getDocumentOrEmpty(final Map<String, Object> value) {
    return value == null || value.isEmpty()
        ? DocumentValue.EMPTY_DOCUMENT
        : new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value));
  }
}

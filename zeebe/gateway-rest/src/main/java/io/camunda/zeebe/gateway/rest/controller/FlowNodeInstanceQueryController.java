/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.service.FlowNodeInstanceServices;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceItem;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/flownode-instances")
public class FlowNodeInstanceQueryController {

  private final FlowNodeInstanceServices flownodeInstanceServices;

  public FlowNodeInstanceQueryController(final FlowNodeInstanceServices flownodeInstanceServices) {
    this.flownodeInstanceServices = flownodeInstanceServices;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FlowNodeInstanceSearchQueryResponse> searchFlownodeInstances(
      @RequestBody(required = false) final FlowNodeInstanceSearchQueryRequest query) {
    return SearchQueryRequestMapper.toFlownodeInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @GetMapping(
      path = "/{flowNodeInstanceKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<FlowNodeInstanceItem> getByKey(
      @PathVariable("flowNodeInstanceKey") final Long flowNodeInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toFlowNodeInstance(
                  flownodeInstanceServices
                      .withAuthentication(RequestMapper.getAuthentication())
                      .getByKey(flowNodeInstanceKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<FlowNodeInstanceSearchQueryResponse> search(
      final FlowNodeInstanceQuery query) {
    try {
      final var result =
          flownodeInstanceServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toFlownodeInstanceSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}

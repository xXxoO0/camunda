/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@CamundaRestController
@RequestMapping("/v2")
public class ResourceController {

  private final ResourceServices resourceServices;

  @Autowired
  public ResourceController(final ResourceServices resourceServices) {
    this.resourceServices = resourceServices;
  }

  @PostMapping(
      path = "/deployments",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> deployResources(
      @RequestPart("resources") final List<MultipartFile> resources,
      @RequestPart(value = "tenantId", required = false) final String tenantId) {

    return RequestMapper.toDeployResourceRequest(resources, tenantId)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::deployResources);
  }

  private CompletableFuture<ResponseEntity<Object>> deployResources(
      final DeployResourcesRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            resourceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .deployResources(request),
        ResponseMapper::toDeployResourceResponse);
  }
}
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.ProcessSearchClient;
import io.camunda.service.query.ProcessInstanceQueryServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CamundaQueryServicesConfiguration {

  @Bean
  public ProcessInstanceQueryServices processInstanceQueryServices(
      final ProcessSearchClient processSearchClient) {
    return new ProcessInstanceQueryServices(processSearchClient);
  }

}

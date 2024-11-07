/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.permission;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.SecurityContextWrapper;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.service.AuthorizationServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PermissionsConfigurer {

  @Bean
  public PermissionsService getPermissionsService(
      final OperateProperties operateProperties,
      final SecurityContextWrapper securityContextWrapper,
      final AuthorizationServices authorizationServices,
      final AuthorizationChecker authorizationChecker) {
    return new PermissionsService(
        operateProperties, securityContextWrapper, authorizationServices, authorizationChecker);
  }
}

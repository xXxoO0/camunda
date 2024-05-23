/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.sso.TokenAuthentication;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Component
public class IdentityAuthorizationService {

  private final Logger logger = LoggerFactory.getLogger(IdentityAuthorizationService.class);

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private LocalValidatorFactoryBean defaultValidator;

  public List<String> getUserGroups() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String accessToken = null;
    final Identity identity = SpringContextHolder.getBean(Identity.class);
    // Extract access token based on authentication type
    if (authentication instanceof IdentityAuthentication) {
      accessToken = ((IdentityAuthentication) authentication).getTokens().getAccessToken();
      return identity.authentication().getGroups(accessToken);
    } else if (authentication instanceof TokenAuthentication) {
      accessToken = ((TokenAuthentication) authentication).getAccessToken();
      final String organization = ((TokenAuthentication) authentication).getOrganization();
      return identity.authentication().getGroups(accessToken, organization);
    }

    // Fallback groups if authentication type is unrecognized or access token is null
    final List<String> defaultGroups = new ArrayList<>();
    defaultGroups.add(IdentityProperties.FULL_GROUP_ACCESS);
    return defaultGroups;
  }

  public boolean isAllowedToStartProcess(final String processDefinitionKey) {
    return !Collections.disjoint(
        getProcessDefinitionsFromAuthorization(),
        Set.of(IdentityProperties.ALL_RESOURCES, processDefinitionKey));
  }

  public List<String> getProcessReadFromAuthorization() {
    return getFromAuthorization(IdentityAuthorization.PROCESS_PERMISSION_READ);
  }

  public List<String> getProcessDefinitionsFromAuthorization() {
    return getFromAuthorization(IdentityAuthorization.PROCESS_PERMISSION_START);
  }

  private Optional<IdentityAuthorization> getIdentityAuthorization() {
    if (!tasklistProperties.getIdentity().isResourcePermissionsEnabled()
        || tasklistProperties.getIdentity().getBaseUrl() == null) {
      return Optional.empty();
    }

    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    return switch (authentication) {
      case final IdentityAuthentication identityAuthentication ->
          Optional.of(identityAuthentication.getAuthorizations());
      case final JwtAuthenticationToken jwtAuthenticationToken -> {
        final Identity identity = SpringContextHolder.getBean(Identity.class);
        yield Optional.of(
            new IdentityAuthorization(
                identity
                    .authorizations()
                    .forToken(jwtAuthenticationToken.getToken().getTokenValue())));
      }

      case final TokenAuthentication tokenAuthentication -> {
        final Identity identity = SpringContextHolder.getBean(Identity.class);
        yield Optional.of(
            new IdentityAuthorization(
                identity
                    .authorizations()
                    .forToken(
                        tokenAuthentication.getAccessToken(),
                        tokenAuthentication.getOrganization())));
      }
      default -> Optional.empty();
    };
  }

  private List<String> getFromAuthorization(final String type) {
    final Optional<IdentityAuthorization> optIdentityAuthorization = getIdentityAuthorization();
    if (optIdentityAuthorization.isEmpty()) {
      return Collections.singletonList(IdentityProperties.ALL_RESOURCES);
    }

    final IdentityAuthorization identityAuthorization = optIdentityAuthorization.get();

    return switch (type) {
      case IdentityAuthorization.PROCESS_PERMISSION_READ ->
          identityAuthorization.getProcessesAllowedToRead();
      case IdentityAuthorization.PROCESS_PERMISSION_START ->
          identityAuthorization.getProcessesAllowedToStart();
      default -> Collections.emptyList();
    };
  }
}
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.application.commons.service.SearchClientDatabaseConfiguration.SearchClientProperties;
import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.clients.ProcessSearchClient;
import io.camunda.search.connect.SearchClientProvider;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@EnableConfigurationProperties(SearchClientProperties.class)
public class SearchClientDatabaseConfiguration {

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.database",
      name = "type",
      havingValue = "opensearch")
  public CamundaSearchClient opensearchClient(final SearchClientProperties configuration) {
    return SearchClientProvider.createOpensearchProvider(configuration);
  }

  @ConfigurationProperties("camunda.database")
  public static final class SearchClientProperties extends ConnectConfiguration {

  }

  @Configuration
  @ConditionalOnProperty(
      prefix = "camunda.database",
      name = "type",
      havingValue = "elasticsearch",
      matchIfMissing = true)
  private static class ESConfiguration {

    @Bean
    public CamundaSearchClient elasticsearchClient(final SearchClientProperties configuration) {
      return SearchClientProvider.createElasticsearchProvider(configuration);
    }

    @Bean
    public ProcessSearchClient processSearchClient(final SearchClientProperties configuration) {
      return SearchClientProvider.createElasticsearchProcessInstanceSearchClient(configuration);
    }
  }
}

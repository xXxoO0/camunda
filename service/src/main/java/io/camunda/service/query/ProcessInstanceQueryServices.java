/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query;

import io.camunda.search.clients.ProcessSearchClient;
import io.camunda.service.SearchQueryService;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.exception.SearchQueryExecutionException;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

// Separation of concerns ... Broker and SearchQuery do different thinks, therefore should not depend on each other
public final class ProcessInstanceQueryServices
    implements SearchQueryService<ProcessInstanceQuery, ProcessInstanceEntity> {

  private final ProcessSearchClient processSearchClient;

  public ProcessInstanceQueryServices(final ProcessSearchClient processSearchClient) {
    this.processSearchClient = processSearchClient;
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> search(
      final ProcessInstanceQuery query,
      final Authentication authentication
  ) {
    return processSearchClient
        .searchProcessInstances(query, authentication)
        .fold(
            (e) -> {
              throw new SearchQueryExecutionException("Failed to execute search query", e);
            },
            (r) -> r);
  }

  // TODO not used?
  public SearchQueryResult<ProcessInstanceEntity> search(
      final Function<ProcessInstanceQuery.Builder, ObjectBuilder<ProcessInstanceQuery>> fn) {
    return search(SearchQueryBuilders.processInstanceSearchQuery(fn), null);
  }

}

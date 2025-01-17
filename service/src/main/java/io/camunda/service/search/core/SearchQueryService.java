/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.core;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.SearchQueryBase;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.ApiServices;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public abstract class SearchQueryService<T extends ApiServices<T>, Q extends SearchQueryBase, D>
    extends ApiServices<T> {

  protected SearchQueryService(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
  }

  public abstract SearchQueryResult<D> search(final Q query);

  protected <E> E getSingleResultOrThrow(
      final SearchQueryResult<E> searchQueryResult, final long key, final String entityTypeLabel) {
    if (searchQueryResult.total() < 1) {
      throw new NotFoundException(String.format("%s with key %d not found", entityTypeLabel, key));
    } else if (searchQueryResult.total() > 1) {
      throw new CamundaSearchException(
          String.format("Found %s with key %d more than once", entityTypeLabel, key));
    } else {
      return searchQueryResult.items().stream().findFirst().orElseThrow();
    }
  }
}

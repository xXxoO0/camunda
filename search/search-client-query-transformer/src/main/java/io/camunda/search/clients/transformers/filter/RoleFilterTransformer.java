/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.RoleFilter;
import java.util.List;

public class RoleFilterTransformer implements FilterTransformer<RoleFilter> {

  @Override
  public SearchQuery toSearchQuery(final RoleFilter filter) {

    return and(
        filter.roleKey() == null ? null : term("roleKey", filter.roleKey()),
        filter.name() == null ? null : term("name", filter.name()));
  }

  @Override
  public List<String> toIndices(final RoleFilter filter) {
    return List.of("identity-role-8.7.0_alias");
  }
}

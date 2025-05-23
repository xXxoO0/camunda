/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record BatchOperationFilter(
    List<String> batchOperationIds, List<String> operationTypes, List<String> state)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<BatchOperationFilter> {

    private List<String> batchOperationIds;
    private List<String> operationTypes;
    private List<String> state;

    public Builder batchOperationIds(final String value, final String... values) {
      return batchOperationIds(collectValues(value, values));
    }

    public Builder batchOperationIds(final List<String> values) {
      batchOperationIds = addValuesToList(batchOperationIds, values);
      return this;
    }

    public Builder operationTypes(final String value, final String... values) {
      return operationTypes(collectValues(value, values));
    }

    public Builder operationTypes(final List<String> values) {
      operationTypes = addValuesToList(operationTypes, values);
      return this;
    }

    public Builder state(final String value, final String... values) {
      return state(collectValues(value, values));
    }

    public Builder state(final List<String> values) {
      state = addValuesToList(state, values);
      return this;
    }

    @Override
    public BatchOperationFilter build() {
      return new BatchOperationFilter(
          Objects.requireNonNullElse(batchOperationIds, Collections.emptyList()),
          Objects.requireNonNullElse(operationTypes, Collections.emptyList()),
          Objects.requireNonNullElse(state, Collections.emptyList()));
    }
  }
}

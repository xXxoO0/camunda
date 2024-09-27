/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.ObjectBuilder;
import java.util.List;

public record FormFilter(List<Long> keys) implements FilterBase {

  public static final class Builder implements ObjectBuilder<FormFilter> {
    private List<Long> keys;

    public Builder keys(final Long value, final Long... values) {
      return keys(collectValues(value, values));
    }

    public Builder keys(final List<Long> values) {
      keys = addValuesToList(keys, values);
      return this;
    }

    @Override
    public FormFilter build() {
      return new FormFilter(keys);
    }
  }
}
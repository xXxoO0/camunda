/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

public class EventImportConfiguration {

  private boolean enabled;
  private int maxPageSize;

  public EventImportConfiguration(final boolean enabled, final int maxPageSize) {
    this.enabled = enabled;
    this.maxPageSize = maxPageSize;
  }

  protected EventImportConfiguration() {}

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getMaxPageSize() {
    return maxPageSize;
  }

  public void setMaxPageSize(final int maxPageSize) {
    this.maxPageSize = maxPageSize;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventImportConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnabled() ? 79 : 97);
    result = result * PRIME + getMaxPageSize();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventImportConfiguration)) {
      return false;
    }
    final EventImportConfiguration other = (EventImportConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    if (getMaxPageSize() != other.getMaxPageSize()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventImportConfiguration(enabled="
        + isEnabled()
        + ", maxPageSize="
        + getMaxPageSize()
        + ")";
  }
}
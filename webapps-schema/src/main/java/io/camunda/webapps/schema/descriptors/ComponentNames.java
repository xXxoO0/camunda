/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

public enum ComponentNames {
  TASK_LIST("tasklist"),
  OPERATE("operate");

  private final String value;

  ComponentNames(final String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return value;
  }
}
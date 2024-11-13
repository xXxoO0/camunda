/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camunda.migration.process")
public class ProcessMigrationProperties {

  private int batchSize = 20;
  private int maxRetries = 3;
  private int backoffInSeconds = 1;
  private ConnectConfiguration connect = new ConnectConfiguration();

  public ConnectConfiguration getConnect() {
    return connect;
  }

  public void setConnect(final ConnectConfiguration connect) {
    this.connect = connect;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(final int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public int getBackoffInSeconds() {
    return backoffInSeconds;
  }

  public void setBackoffInSeconds(final int backoffInSeconds) {
    this.backoffInSeconds = backoffInSeconds;
  }
}

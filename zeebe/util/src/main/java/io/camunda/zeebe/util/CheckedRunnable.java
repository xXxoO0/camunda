/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import org.agrona.LangUtil;

/** A simple extension of runnable which allows for exceptions to be thrown. */
@FunctionalInterface
// ignore generic exception warning here as we want to allow for any exception to be thrown
@SuppressWarnings("java:S112")
public interface CheckedRunnable {
  void run() throws Exception;

  static java.lang.Runnable toRunnable(final CheckedRunnable runnable) {
    return () -> {
      try {
        runnable.run();
      } catch (final Exception e) {
        LangUtil.rethrowUnchecked(e);
      }
    };
  }
}

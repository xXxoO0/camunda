/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public class ExternalApiRewriteFilter implements Filter {

  private static final String EXTERNAL_API_PREFIX = "/external/api";
  private static final String API_EXTERNAL_PREFIX = "/api/external";

  private String clusterId;

  public ExternalApiRewriteFilter(String clusterId) {
    this.clusterId = clusterId;
    if (this.clusterId != null) {
      this.clusterId = this.clusterId.trim();
    }
  }

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    final HttpServletRequest httpRequest = (HttpServletRequest) request;
    String requestURI = httpRequest.getRequestURI();

    if (this.clusterId != null && this.clusterId.length() > 0) {
      requestURI = requestURI.replaceFirst("/" + clusterId, "");
    }

    final String contextPath = httpRequest.getContextPath();
    if (requestURI.startsWith(contextPath + EXTERNAL_API_PREFIX)) {
      // NOTE: When we compute the new request URI, we should NOT include the contextPath
      //   in it, as the dispatcher will include it on its own.
      final String rewrittenURI =
          requestURI.replaceFirst(contextPath + EXTERNAL_API_PREFIX, API_EXTERNAL_PREFIX);
      RequestDispatcher dispatcher = request.getRequestDispatcher(rewrittenURI);
      dispatcher.forward(request, response);
    } else {
      chain.doFilter(request, response);
    }
  }
}

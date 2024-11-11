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
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

public class URLRedirectFilter implements Filter {

  private static final String WEBAPP_PATH = "/webapp";

  private final Pattern redirectPattern;
  private final String redirectPath;

  public URLRedirectFilter(final String regex, final String redirectPath) {
    redirectPattern = Pattern.compile(regex);
    this.redirectPath = redirectPath;
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final HttpServletRequest httpRequest = (HttpServletRequest) request;
    final HttpServletResponse httpResponse = (HttpServletResponse) response;
    final String requestPath = httpRequest.getRequestURI();
    final String contextPath = httpRequest.getContextPath();

    /* Handle missing trailing slash to home */
    if (contextPath.equals(requestPath)) {
      httpResponse.sendRedirect(redirectPath);
      return;
    }

    /* Validate requests to the home page */
    if ((contextPath + "/").equals(requestPath)) {
      chain.doFilter(request, response);
      return;
    }

    /* Validate requests to the static resources */
    String staticRequestPath = requestPath;
    if (!"".equals(contextPath)) {
      staticRequestPath = staticRequestPath.substring(contextPath.length());
    }
    final String staticFilePath = WEBAPP_PATH + staticRequestPath;
    final InputStream fileStream = getClass().getResourceAsStream(staticFilePath);
    if (fileStream != null) {
      chain.doFilter(request, response);
      return;
    }

    if (redirectPattern.matcher(requestPath).matches()) {
      httpResponse.sendRedirect(redirectPath);
    } else {
      chain.doFilter(request, response);
    }
  }
}

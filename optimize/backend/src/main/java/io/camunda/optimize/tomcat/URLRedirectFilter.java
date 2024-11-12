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
import org.slf4j.Logger;

public class URLRedirectFilter implements Filter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(URLRedirectFilter.class);
  private static final String WEBAPP_PATH = "/webapp";

  private final Pattern redirectPattern;
  private final String redirectPath;
  private final String clusterId;

  public URLRedirectFilter(final String regex, final String redirectPath, String clusterId) {
    redirectPattern = Pattern.compile(regex);
    this.redirectPath = redirectPath;
    this.clusterId = clusterId;
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final HttpServletRequest httpRequest = (HttpServletRequest) request;
    final HttpServletResponse httpResponse = (HttpServletResponse) response;
    final String requestPath = httpRequest.getRequestURI();

    LOG.info("requestPath={}", requestPath);

    /* urlSuffix can be the empty string or (/contexPpath xor /clusterId) */
    String urlSuffix = httpRequest.getContextPath();
    if (!this.clusterId.isEmpty()) {
      urlSuffix = "/" + this.clusterId;
    }

    LOG.info("urlSuffix=" + urlSuffix);

    /* Handle missing trailing slash to home */
    if (urlSuffix.equals(requestPath)) {
      LOG.info("missing trailing slash. Redirecting to " + redirectPath);
      httpResponse.sendRedirect(redirectPath);
      return;
    }

    /* Validate requests to the home page */
    if ((urlSuffix + "/").equals(requestPath)) {
      LOG.info("home page request. Passing through.");
      chain.doFilter(request, response);
      return;
    }

    /* Validate requests to the static resources */
    String staticRequestPath = requestPath;
    if (!urlSuffix.isEmpty()) {
      staticRequestPath = staticRequestPath.substring(urlSuffix.length());
    }
    final String staticFilePath = WEBAPP_PATH + staticRequestPath;
    final InputStream fileStream = getClass().getResourceAsStream(staticFilePath);
    if (fileStream != null) {
      LOG.info("detected static resource: " + staticFilePath);
      chain.doFilter(request, response);
      return;
    }

    /* Redirect URLs that do not pass our validity rule */
    if (redirectPattern.matcher(requestPath).matches()) {
      httpResponse.sendRedirect(redirectPath);
      return;
    }

    chain.doFilter(request, response);
  }
}

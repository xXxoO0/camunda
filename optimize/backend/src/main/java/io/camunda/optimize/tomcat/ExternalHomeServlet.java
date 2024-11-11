/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.util.StreamUtils;

public class ExternalHomeServlet extends HttpServlet {

  private static final String INDEX_FILE = "/index.html";
  private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
  private static final String WEBAPP_PATH = "/webapp";

  @Override
  protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
      throws ServletException, IOException {
    String filename = request.getPathInfo(); // e.g., /someFile.txt
    if (filename == null || "/".equals(filename)) {
      filename = INDEX_FILE;
    }

    final String resourcePath = WEBAPP_PATH + filename;
    final InputStream fileStream = this.getClass().getResourceAsStream(resourcePath);

    final String mimeType = getServletContext().getMimeType(resourcePath);
    response.setContentType(mimeType != null ? mimeType : DEFAULT_MIME_TYPE);
    StreamUtils.copy(fileStream, response.getOutputStream());
    response.flushBuffer();
  }
}

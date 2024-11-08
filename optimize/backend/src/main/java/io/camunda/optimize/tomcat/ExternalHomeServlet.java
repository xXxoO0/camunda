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
import java.io.File;
import java.io.IOException;

public class ExternalHomeServlet extends HttpServlet {
  private final String webappPath =
      getClass().getClassLoader().getResource("webapp").toExternalForm().replaceFirst("file:", "");

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String filename = request.getPathInfo(); // e.g., /someFile.txt
    if (filename == null || filename.equals("/")) {
      filename = "/index.html";
    }

    File file = new File(webappPath, filename.substring(1));
    if (!file.exists() || file.isDirectory()) {
      file = new File(webappPath, "/index.html");
    }

    serveStaticFile(file, response);
  }

  private void serveStaticFile(File file, HttpServletResponse response) throws IOException {
    response.setContentType(getServletContext().getMimeType(file.getName()));
    java.nio.file.Files.copy(file.toPath(), response.getOutputStream());
    response.flushBuffer();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler.session;

import io.camunda.search.security.SessionDocumentStorageClient;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class CamundaSessionRepository
    implements org.springframework.session.SessionRepository<WebSession> {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaSessionRepository.class);

  private final ThreadPoolTaskScheduler sessionThreadScheduler;
  private final SessionDocumentMapper sessionDocumentMapper;
  private final SessionDocumentStorageClient sessionStorageClient;
  private final HttpServletRequest request;

  public CamundaSessionRepository(
      final ThreadPoolTaskScheduler sessionThreadScheduler,
      final SessionDocumentMapper sessionDocumentMapper,
      final SessionDocumentStorageClient sessionStorageClient,
      final HttpServletRequest request) {
    this.sessionThreadScheduler = sessionThreadScheduler;
    this.sessionDocumentMapper = sessionDocumentMapper;
    this.sessionStorageClient = sessionStorageClient;
    this.request = request;
  }

  @PostConstruct
  public void init() {
    startExpiredSessionCheck();
  }

  private void startExpiredSessionCheck() {
    sessionThreadScheduler.scheduleAtFixedRate(
        () ->
            sessionStorageClient.consumeSessions(
                doc -> purgeAndFetchWebSession((String) doc.get(WebSession.ID), doc)),
        Duration.ofMillis(1000));
  }

  @Override
  public WebSession createSession() {
    final String sessionId = UUID.randomUUID().toString().replace("-", "");

    final WebSession session = new WebSession(sessionId);
    LOGGER.debug(
        "Create session {} with maxInactiveInterval {} s",
        session,
        session.getMaxInactiveInterval());
    return session;
  }

  @Override
  public void save(final WebSession session) {
    LOGGER.debug("Save session {}", session);
    if (session.shouldBeDeleted()) {
      deleteById(session.getId());
      return;
    }
    if (session.isChanged()) {
      LOGGER.debug("Session {} changed, save in Elasticsearch.", session);
      final Map<String, Object> document = sessionDocumentMapper.sessionToDocument(session);
      sessionStorageClient.createOrUpdateSessionDocument(session.getId(), document);
      session.clearChangeFlag();
    }
  }

  @Override
  public WebSession findById(final String id) {
    LOGGER.debug("Retrieve session {} from Elasticsearch", id);
    return purgeAndFetchWebSession(id, sessionStorageClient.getSessionDocument(id));
  }

  @Override
  public void deleteById(final String id) {
    LOGGER.debug("Delete session {}", id);
    sessionStorageClient.deleteSessionDocument(id);
  }

  private WebSession purgeAndFetchWebSession(final String id, final Map<String, Object> document) {
    final WebSession session = sessionDocumentMapper.documentToSession(request, document);
    if (session == null || session.shouldBeDeleted()) {
      deleteById(id);
      return null;
    }
    return session;
  }
}
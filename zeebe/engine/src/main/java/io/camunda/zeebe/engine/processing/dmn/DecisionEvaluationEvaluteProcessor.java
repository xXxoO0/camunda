/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.dmn;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.processing.common.DecisionBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.collection.Tuple;

public class DecisionEvaluationEvaluteProcessor
    implements TypedRecordProcessor<DecisionEvaluationRecord> {

  private static final String ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED =
      "Expected either a decision id or a valid decision key, but none provided";

  private final DecisionBehavior decisionBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  public DecisionEvaluationEvaluteProcessor(
      final DecisionBehavior decisionBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior) {

    this.decisionBehavior = decisionBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<DecisionEvaluationRecord> command) {

    final DecisionEvaluationRecord record = command.getValue();
    final Either<Failure, PersistedDecision> decisionOrFailure = getDecision(record);

    if (decisionOrFailure.isRight()) {
      final var decision = decisionOrFailure.get();
      final var decisionId = bufferAsString(decision.getDecisionId());
      final var authRequest =
          new AuthorizationRequest(
                  command, AuthorizationResourceType.DECISION_DEFINITION, PermissionType.CREATE)
              .addResourceId(decisionId);

      if (!authCheckBehavior.isAuthorized(authRequest)) {
        final var reason =
            AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE.formatted(
                authRequest.getPermissionType(),
                authRequest.getResourceType(),
                "decision id '%s'".formatted(decisionId));
        responseWriter.writeRejectionOnCommand(command, RejectionType.UNAUTHORIZED, reason);
        rejectionWriter.appendRejection(command, RejectionType.UNAUTHORIZED, reason);
        return;
      }
    }

    decisionOrFailure
        .flatMap(decisionBehavior::findParsedDrgByDecision)
        .ifRightOrLeft(
            drg -> {
              final var decision = decisionOrFailure.get();
              final var variables = record.getVariablesBuffer();
              final var evaluationResult =
                  decisionBehavior.evaluateDecisionInDrg(
                      drg, BufferUtil.bufferAsString(decision.getDecisionId()), variables);

              final Tuple<DecisionEvaluationIntent, DecisionEvaluationRecord>
                  evaluationRecordTuple =
                      decisionBehavior.createDecisionEvaluationEvent(decision, evaluationResult);

              final var evaluationRecordKey = keyGenerator.nextKey();
              stateWriter.appendFollowUpEvent(
                  evaluationRecordKey,
                  evaluationRecordTuple.getLeft(),
                  evaluationRecordTuple.getRight());
              responseWriter.writeEventOnCommand(
                  evaluationRecordKey,
                  evaluationRecordTuple.getLeft(),
                  evaluationRecordTuple.getRight(),
                  command);
            },
            failure -> {
              final String reason = failure.getMessage();
              responseWriter.writeRejectionOnCommand(
                  command, RejectionType.INVALID_ARGUMENT, reason);
              rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, reason);
            });
  }

  private Either<Failure, PersistedDecision> getDecision(final DecisionEvaluationRecord record) {

    final String decisionId = record.getDecisionId();
    final long decisionKey = record.getDecisionKey();

    if (!decisionId.isEmpty()) {
      return decisionBehavior.findLatestDecisionByIdAndTenant(decisionId, record.getTenantId());
      // TODO: expand DecisionState API to find decisions by ID AND VERSION (#11230)
    } else if (decisionKey > -1L) {
      return decisionBehavior.findDecisionByKeyAndTenant(decisionKey, record.getTenantId());
    } else {
      // if both ID and KEY are missing
      return Either.left(new Failure(ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED));
    }
  }
}

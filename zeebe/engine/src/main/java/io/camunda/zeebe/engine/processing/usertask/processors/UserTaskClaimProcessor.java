/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public final class UserTaskClaimProcessor implements UserTaskCommandProcessor {

  private static final String DEFAULT_ACTION = "claim";

  private static final String INVALID_USER_TASK_ASSIGNEE_MESSAGE =
      "Expected to claim user task with key '%d', but it has already been assigned";
  private static final String INVALID_USER_TASK_EMPTY_ASSIGNEE_MESSAGE =
      "Expected to claim user task with key '%d', but provided assignee is empty";

  private final UserTaskState userTaskState;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final UserTaskCommandPreconditionChecker preconditionChecker;

  public UserTaskClaimProcessor(
      final ProcessingState state,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior) {
    userTaskState = state.getUserTaskState();
    stateWriter = writers.state();
    responseWriter = writers.response();
    preconditionChecker =
        new UserTaskCommandPreconditionChecker(
            List.of(LifecycleState.CREATED),
            "claim",
            UserTaskClaimProcessor::checkClaim,
            state.getUserTaskState(),
            authCheckBehavior);
  }

  @Override
  public Either<Tuple<RejectionType, String>, UserTaskRecord> validateCommand(
      final TypedRecord<UserTaskRecord> command) {
    return preconditionChecker.check(command);
  }

  @Override
  public void onCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    userTaskRecord.setAssignee(command.getValue().getAssignee());
    userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION));

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNING, userTaskRecord);
  }

  /*
   Note: This method finalizes the `CLAIM` command only when there are no `assignment` listeners
   for the user task. If `assignment` listeners are defined, the `onFinalizeCommand` method
   from `UserTaskAssignProcessor` will handle this logic instead.

   This occurs because both `CLAIM` and `ASSIGN` commands transition the user task to the
   `ASSIGNING` lifecycle state, making it indistinguishable which command initially led to this state.
   Therefore, `UserTaskAssignProcessor` is selected to finalize the command after all task listeners
   are processed when the user task is in the `ASSIGNING` state.

   This approach is acceptable as long as `CLAIM` and `ASSIGN` share identical finalization logic.
   Any need for distinct handling would require a further update to this behavior.
  */
  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    userTaskRecord.setAssignee(command.getValue().getAssignee());
    userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION));

    if (command.hasRequestMetadata()) {
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord);
      responseWriter.writeEventOnCommand(
          userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord, command);
    } else {
      final var recordRequestMetadata = userTaskState.findRecordRequestMetadata(userTaskKey);
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord);

      recordRequestMetadata.ifPresent(
          metadata ->
              responseWriter.writeResponse(
                  userTaskKey,
                  UserTaskIntent.ASSIGNED,
                  userTaskRecord,
                  ValueType.USER_TASK,
                  metadata.getRequestId(),
                  metadata.getRequestStreamId()));
    }
  }

  private static Either<Tuple<RejectionType, String>, UserTaskRecord> checkClaim(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {

    final long userTaskKey = command.getKey();
    final String newAssignee = command.getValue().getAssignee();
    if (newAssignee.isBlank()) {
      return Either.left(
          Tuple.of(
              RejectionType.INVALID_STATE,
              String.format(INVALID_USER_TASK_EMPTY_ASSIGNEE_MESSAGE, userTaskKey)));
    }

    final String currentAssignee = userTaskRecord.getAssignee();
    final boolean canClaim = currentAssignee.isBlank() || currentAssignee.equals(newAssignee);
    if (!canClaim) {
      return Either.left(
          Tuple.of(
              RejectionType.INVALID_STATE,
              String.format(INVALID_USER_TASK_ASSIGNEE_MESSAGE, userTaskKey)));
    }

    return Either.right(userTaskRecord);
  }
}

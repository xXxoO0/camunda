/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.*;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.UserTaskState;
import io.camunda.db.rdbms.write.service.UserTaskWriter;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.time.OffsetDateTime;

/** Based on UserTaskRecordToTaskEntityMapper */
public class JobUserTaskExportHandler implements RdbmsExportHandler<JobRecordValue> {

  private final UserTaskWriter userTaskWriter;

  public JobUserTaskExportHandler(final UserTaskWriter userTaskWriter) {
    this.userTaskWriter = userTaskWriter;
  }

  @Override
  public boolean canExport(final Record<JobRecordValue> record) {
    return record.getValue() != null
        && record.getValue().getType().equals(Protocol.USER_TASK_JOB_TYPE)
        && record.getIntent() != null
        && !record.getIntent().equals(JobIntent.TIMED_OUT);
  }

  @Override
  public void export(final Record<JobRecordValue> record) {
    switch (record.getIntent()) {
      case CREATED -> userTaskWriter.create(map(record, UserTaskState.CREATED, null));
      case CANCELED ->
          userTaskWriter.update(
              map(record, UserTaskState.CANCELED, DateUtil.toOffsetDateTime(record.getTimestamp())));
      case COMPLETED ->
          userTaskWriter.update(
              map(record,
                  UserTaskState.COMPLETED,
                  DateUtil.toOffsetDateTime(record.getTimestamp())));
      case MIGRATED -> userTaskWriter.update(map(record, UserTaskState.CREATED, null));
      default -> userTaskWriter.update(map(record, null, null));
    }
  }

  private UserTaskDbModel map(
      final Record<JobRecordValue> record,
      final UserTaskState state,
      final OffsetDateTime completionTime) {
    final JobRecordValue recordValue = record.getValue();
    return new UserTaskDbModel.Builder()
        .key(record.getKey())
        .flowNodeBpmnId(recordValue.getElementId())
        .processDefinitionId(recordValue.getBpmnProcessId())
        .creationTime(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .completionTime(completionTime)
        /**.assignee(recordValue.getAssignee())
        .state(state)
        .formKey(recordValue.getFormKey())
        .processDefinitionKey(recordValue.getProcessDefinitionKey())
        .processInstanceKey(recordValue.getProcessInstanceKey())
        .elementInstanceKey(recordValue.getElementInstanceKey())
        .tenantId(recordValue.getTenantId())
        .dueDate(DateUtil.toOffsetDateTime(recordValue.getDueDate()))
        .followUpDate(DateUtil.toOffsetDateTime(recordValue.getFollowUpDate()))
        .candidateGroups(recordValue.getCandidateGroupsList())
        .candidateUsers(recordValue.getCandidateUsersList())
        .externalFormReference(recordValue.getExternalFormReference())
        .processDefinitionVersion(recordValue.getProcessDefinitionVersion())
        .customHeaders(recordValue.getCustomHeaders())
        .priority(recordValue.getPriority())**/
        .build();
  }
}

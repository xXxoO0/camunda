/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

import io.camunda.document.api.DocumentLink;
import io.camunda.service.DocumentServices.DocumentReferenceResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.job.JobActivationResult;
import io.camunda.zeebe.gateway.protocol.rest.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.rest.CreateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentDecision;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentDecisionRequirements;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentForm;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentMetadata;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentProcess;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentResponse;
import io.camunda.zeebe.gateway.protocol.rest.DocumentMetadata;
import io.camunda.zeebe.gateway.protocol.rest.DocumentReference;
import io.camunda.zeebe.gateway.protocol.rest.DocumentReference.CamundaDocumentTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.EvaluateDecisionResponse;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionInputItem;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionItem;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionOutputItem;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResponse;
import io.camunda.zeebe.gateway.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.zeebe.gateway.protocol.rest.MessageCorrelationResponse;
import io.camunda.zeebe.gateway.protocol.rest.MessagePublicationResponse;
import io.camunda.zeebe.gateway.protocol.rest.RoleCreateResponse;
import io.camunda.zeebe.gateway.protocol.rest.SignalBroadcastResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserCreateResponse;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedOutputValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ResponseMapper {

  /**
   * Date format <code>uuuu-MM-dd'T'HH:mm:ss.SSS[SSSSSS]Z</code>, always creating
   * millisecond-precision outputs at least, with up to 9 digits of nanosecond precision if present
   * in the date. Examples:
   *
   * <ul>
   *   <li>2020-11-11T10:10.11.123Z
   *   <li>2020-11-11T10:10.00.000Z
   *   <li>2020-11-11T10:10.00.00301Z
   *   <li>2020-11-11T10:10.00.003013456Z
   * </ul>
   */
  private static final DateTimeFormatter DATE_RESPONSE_MAPPER =
      new DateTimeFormatterBuilder()
          .parseCaseInsensitive()
          .append(DateTimeFormatter.ISO_LOCAL_DATE)
          .appendLiteral('T')
          .appendValue(HOUR_OF_DAY, 2)
          .appendLiteral(':')
          .appendValue(MINUTE_OF_HOUR, 2)
          .appendLiteral(':')
          .appendValue(SECOND_OF_MINUTE, 2)
          .appendFraction(NANO_OF_SECOND, 3, 9, true)
          .parseLenient()
          .appendOffsetId()
          .parseStrict()
          .toFormatter();

  public static String formatDate(final OffsetDateTime date) {
    return date == null ? null : DATE_RESPONSE_MAPPER.format(date);
  }

  public static JobActivationResult<JobActivationResponse> toActivateJobsResponse(
      final io.camunda.zeebe.gateway.impl.job.JobActivationResponse activationResponse) {
    final Iterator<LongValue> jobKeys = activationResponse.brokerResponse().jobKeys().iterator();
    final Iterator<JobRecord> jobs = activationResponse.brokerResponse().jobs().iterator();

    final JobActivationResponse response = new JobActivationResponse();

    while (jobKeys.hasNext() && jobs.hasNext()) {
      final LongValue jobKey = jobKeys.next();
      final JobRecord job = jobs.next();
      final ActivatedJob activatedJob = toActivatedJob(jobKey.getValue(), job);

      response.addJobsItem(activatedJob);
    }

    return new RestJobActivationResult(response);
  }

  private static ActivatedJob toActivatedJob(final long jobKey, final JobRecord job) {
    return new ActivatedJob()
        .jobKey(jobKey)
        .type(job.getType())
        .processDefinitionId(job.getBpmnProcessId())
        .elementId(job.getElementId())
        .processInstanceKey(job.getProcessInstanceKey())
        .processDefinitionVersion(job.getProcessDefinitionVersion())
        .processDefinitionKey(job.getProcessDefinitionKey())
        .elementInstanceKey(job.getElementInstanceKey())
        .worker(bufferAsString(job.getWorkerBuffer()))
        .retries(job.getRetries())
        .deadline(job.getDeadline())
        .variables(job.getVariables())
        .customHeaders(job.getCustomHeadersObjectMap())
        .tenantId(job.getTenantId());
  }

  public static ResponseEntity<Object> toMessageCorrelationResponse(
      final MessageCorrelationRecord brokerResponse) {
    final var response =
        new MessageCorrelationResponse()
            .messageKey(brokerResponse.getMessageKey())
            .tenantId(brokerResponse.getTenantId())
            .processInstanceKey(brokerResponse.getProcessInstanceKey());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toDocumentReference(
      final DocumentReferenceResponse response) {
    final var internalMetadata = response.metadata();
    final var externalMetadata =
        new DocumentMetadata()
            .expiresAt(
                Optional.ofNullable(internalMetadata.expiresAt())
                    .map(Object::toString)
                    .orElse(null))
            .fileName(internalMetadata.fileName())
            .size(internalMetadata.size())
            .contentType(internalMetadata.contentType());
    Optional.ofNullable(internalMetadata.customProperties())
        .ifPresent(map -> map.forEach(externalMetadata::putCustomPropertiesItem));
    final var reference =
        new DocumentReference()
            .camundaDocumentType(CamundaDocumentTypeEnum.CAMUNDA)
            .documentId(response.documentId())
            .storeId(response.storeId())
            .metadata(externalMetadata);
    return new ResponseEntity<>(reference, HttpStatus.CREATED);
  }

  public static ResponseEntity<Object> toDocumentLinkResponse(final DocumentLink documentLink) {
    final var externalDocumentLink = new io.camunda.zeebe.gateway.protocol.rest.DocumentLink();
    externalDocumentLink.setExpiresAt(documentLink.expiresAt().toString());
    externalDocumentLink.setUrl(documentLink.link());
    return new ResponseEntity<>(externalDocumentLink, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toDeployResourceResponse(
      final DeploymentRecord brokerResponse) {
    final var response =
        new DeploymentResponse()
            .deploymentKey(brokerResponse.getDeploymentKey())
            .tenantId(brokerResponse.getTenantId());
    addDeployedProcess(response, brokerResponse.getProcessesMetadata());
    addDeployedDecision(response, brokerResponse.decisionsMetadata());
    addDeployedDecisionRequirements(response, brokerResponse.decisionRequirementsMetadata());
    addDeployedForm(response, brokerResponse.formMetadata());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toMessagePublicationResponse(
      final BrokerResponse<MessageRecord> brokerResponse) {

    final var response =
        new MessagePublicationResponse()
            .messageKey(brokerResponse.getKey())
            .tenantId(brokerResponse.getResponse().getTenantId());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private static void addDeployedForm(
      final DeploymentResponse response, final ValueArray<FormMetadataRecord> formMetadataRecords) {
    formMetadataRecords.stream()
        .map(
            form ->
                new DeploymentForm()
                    .formId(form.getFormId())
                    .version(form.getVersion())
                    .formKey(form.getFormKey())
                    .resourceName(form.getResourceName())
                    .tenantId(form.getTenantId()))
        .map(deploymentForm -> new DeploymentMetadata().form(deploymentForm))
        .forEach(response::addDeploymentsItem);
  }

  private static void addDeployedDecisionRequirements(
      final DeploymentResponse response,
      final ValueArray<DecisionRequirementsMetadataRecord> decisionRequirementsMetadataRecords) {
    decisionRequirementsMetadataRecords.stream()
        .map(
            decisionRequirement ->
                new DeploymentDecisionRequirements()
                    .decisionRequirementsId(decisionRequirement.getDecisionRequirementsId())
                    .version(decisionRequirement.getDecisionRequirementsVersion())
                    .name(decisionRequirement.getDecisionRequirementsName())
                    .tenantId(decisionRequirement.getTenantId())
                    .decisionRequirementsKey(decisionRequirement.getDecisionRequirementsKey())
                    .resourceName(decisionRequirement.getResourceName()))
        .map(
            deploymentDecisionRequirement ->
                new DeploymentMetadata().decisionRequirements(deploymentDecisionRequirement))
        .forEach(response::addDeploymentsItem);
  }

  private static void addDeployedDecision(
      final DeploymentResponse response, final ValueArray<DecisionRecord> decisionRecords) {
    decisionRecords.stream()
        .map(
            decision ->
                new DeploymentDecision()
                    .decisionDefinitionId(decision.getDecisionId())
                    .version(decision.getVersion())
                    .decisionDefinitionKey(decision.getDecisionKey())
                    .name(decision.getDecisionName())
                    .tenantId(decision.getTenantId())
                    .decisionRequirementsId(decision.getDecisionRequirementsId())
                    .decisionRequirementsKey(decision.getDecisionRequirementsKey()))
        .map(deploymentDecision -> new DeploymentMetadata().decisionDefinition(deploymentDecision))
        .forEach(response::addDeploymentsItem);
  }

  private static void addDeployedProcess(
      final DeploymentResponse response, final List<ProcessMetadataValue> processesMetadata) {
    processesMetadata.stream()
        .map(
            process ->
                new DeploymentProcess()
                    .processDefinitionId(process.getBpmnProcessId())
                    .processDefinitionVersion(process.getVersion())
                    .processDefinitionKey(process.getProcessDefinitionKey())
                    .tenantId(process.getTenantId())
                    .resourceName(process.getResourceName()))
        .map(deploymentProcess -> new DeploymentMetadata().processDefinition(deploymentProcess))
        .forEach(response::addDeploymentsItem);
  }

  public static ResponseEntity<Object> toCreateProcessInstanceResponse(
      final ProcessInstanceCreationRecord brokerResponse) {
    return buildCreateProcessInstanceResponse(
        brokerResponse.getProcessDefinitionKey(),
        brokerResponse.getBpmnProcessId(),
        brokerResponse.getVersion(),
        brokerResponse.getProcessInstanceKey(),
        brokerResponse.getTenantId(),
        null);
  }

  public static ResponseEntity<Object> toCreateProcessInstanceWithResultResponse(
      final ProcessInstanceResultRecord brokerResponse) {
    return buildCreateProcessInstanceResponse(
        brokerResponse.getProcessDefinitionKey(),
        brokerResponse.getBpmnProcessId(),
        brokerResponse.getVersion(),
        brokerResponse.getProcessInstanceKey(),
        brokerResponse.getTenantId(),
        brokerResponse.getVariables());
  }

  private static ResponseEntity<Object> buildCreateProcessInstanceResponse(
      final Long processDefinitionKey,
      final String bpmnProcessId,
      final Integer version,
      final Long processInstanceKey,
      final String tenantId,
      final Map<String, Object> variables) {
    final var response =
        new CreateProcessInstanceResponse()
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionId(bpmnProcessId)
            .processDefinitionVersion(version)
            .processInstanceKey(processInstanceKey)
            .tenantId(tenantId);
    if (variables != null) {
      response.variables(variables);
    }

    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toSignalBroadcastResponse(
      final BrokerResponse<SignalRecord> brokerResponse) {
    final var response =
        new SignalBroadcastResponse()
            .signalKey(brokerResponse.getKey())
            .tenantId(brokerResponse.getResponse().getTenantId());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toUserCreateResponse(final UserRecord userRecord) {
    final var response = new UserCreateResponse().userKey(userRecord.getUserKey());
    return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
  }

  public static ResponseEntity<Object> toRoleCreateResponse(final RoleRecord roleRecord) {
    final var response = new RoleCreateResponse().roleKey(roleRecord.getRoleKey());
    return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
  }

  public static ResponseEntity<Object> toEvaluateDecisionResponse(
      final BrokerResponse<DecisionEvaluationRecord> brokerResponse) {
    final var decisionEvaluationRecord = brokerResponse.getResponse();
    final var response =
        new EvaluateDecisionResponse()
            .decisionDefinitionId(decisionEvaluationRecord.getDecisionId())
            .decisionDefinitionKey(decisionEvaluationRecord.getDecisionKey())
            .decisionDefinitionName(decisionEvaluationRecord.getDecisionName())
            .decisionDefinitionVersion(decisionEvaluationRecord.getDecisionVersion())
            .decisionRequirementsId(decisionEvaluationRecord.getDecisionRequirementsId())
            .decisionRequirementsKey(decisionEvaluationRecord.getDecisionRequirementsKey())
            .output(decisionEvaluationRecord.getDecisionOutput())
            .failedDecisionDefinitionId(decisionEvaluationRecord.getFailedDecisionId())
            .failureMessage(decisionEvaluationRecord.getEvaluationFailureMessage())
            .tenantId(decisionEvaluationRecord.getTenantId())
            .decisionInstanceKey(brokerResponse.getKey());

    buildEvaluatedDecisions(decisionEvaluationRecord, response);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private static void buildEvaluatedDecisions(
      final DecisionEvaluationRecord decisionEvaluationRecord,
      final EvaluateDecisionResponse response) {
    decisionEvaluationRecord.getEvaluatedDecisions().stream()
        .map(
            evaluatedDecision ->
                new EvaluatedDecisionItem()
                    .decisionDefinitionKey(evaluatedDecision.getDecisionKey())
                    .decisionDefinitionId(evaluatedDecision.getDecisionId())
                    .decisionDefinitionName(evaluatedDecision.getDecisionName())
                    .decisionDefinitionVersion(evaluatedDecision.getDecisionVersion())
                    .output(evaluatedDecision.getDecisionOutput())
                    .tenantId(evaluatedDecision.getTenantId())
                    .evaluatedInputs(buildEvaluatedInputs(evaluatedDecision.getEvaluatedInputs()))
                    .matchedRules(buildMatchedRules(evaluatedDecision.getMatchedRules())))
        .forEach(response::addEvaluatedDecisionsItem);
  }

  private static List<MatchedDecisionRuleItem> buildMatchedRules(
      final List<MatchedRuleValue> matchedRuleValues) {
    return matchedRuleValues.stream()
        .map(
            matchedRuleValue ->
                new MatchedDecisionRuleItem()
                    .ruleId(matchedRuleValue.getRuleId())
                    .ruleIndex(matchedRuleValue.getRuleIndex())
                    .evaluatedOutputs(
                        buildEvaluatedOutputs(matchedRuleValue.getEvaluatedOutputs())))
        .toList();
  }

  private static List<EvaluatedDecisionOutputItem> buildEvaluatedOutputs(
      final List<EvaluatedOutputValue> evaluatedOutputs) {
    return evaluatedOutputs.stream()
        .map(
            evaluatedOutput ->
                new EvaluatedDecisionOutputItem()
                    .outputId(evaluatedOutput.getOutputId())
                    .outputName(evaluatedOutput.getOutputName())
                    .outputValue(evaluatedOutput.getOutputValue()))
        .toList();
  }

  private static List<EvaluatedDecisionInputItem> buildEvaluatedInputs(
      final List<EvaluatedInputValue> inputValues) {
    return inputValues.stream()
        .map(
            evaluatedInputValue ->
                new EvaluatedDecisionInputItem()
                    .inputId(evaluatedInputValue.getInputId())
                    .inputName(evaluatedInputValue.getInputName())
                    .inputValue(evaluatedInputValue.getInputValue()))
        .toList();
  }

  static class RestJobActivationResult implements JobActivationResult<JobActivationResponse> {

    private final JobActivationResponse response;

    RestJobActivationResult(final JobActivationResponse response) {
      this.response = response;
    }

    @Override
    public int getJobsCount() {
      return response.getJobs().size();
    }

    @Override
    public List<ActivatedJob> getJobs() {
      return response.getJobs().stream()
          .map(j -> new ActivatedJob(j.getJobKey(), j.getRetries()))
          .toList();
    }

    @Override
    public JobActivationResponse getActivateJobsResponse() {
      return response;
    }

    @Override
    public List<ActivatedJob> getJobsToDefer() {
      return Collections.emptyList();
    }
  }
}

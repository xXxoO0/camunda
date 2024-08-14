/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.VARIABLE_TYPE_JSON;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.VARIABLE_TYPE_OBJECT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableList;
import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.query.variable.ProcessToQueryDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableReportValuesRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import io.camunda.optimize.rest.optimize.dto.VariableDto;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.SneakyThrows;

public class VariablesClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;
  private final ObjectMapper objectMapper;

  public VariablesClient(final Supplier<OptimizeRequestExecutor> requestExecutorSupplier) {
    this.requestExecutorSupplier = requestExecutorSupplier;
    objectMapper =
        new ObjectMapper()
            .setDateFormat(new SimpleDateFormat(OPTIMIZE_DATE_FORMAT))
            .enable(SerializationFeature.INDENT_OUTPUT);
  }

  public List<ProcessVariableNameResponseDto> getProcessVariableNames(
      ProcessVariableNameRequestDto variableRequestDtos) {
    return getRequestExecutor()
        .buildProcessVariableNamesRequest(variableRequestDtos)
        .executeAndReturnList(
            ProcessVariableNameResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<ProcessVariableNameResponseDto> getProcessVariableNamesForReportIds(
      final List<String> reportIds) {
    return getRequestExecutor()
        .buildProcessVariableNamesForReportsRequest(reportIds)
        .executeAndReturnList(
            ProcessVariableNameResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<String> getProcessVariableValuesForReports(
      final ProcessVariableReportValuesRequestDto dto) {
    return getRequestExecutor()
        .buildProcessVariableValuesForReportsRequest(dto)
        .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());
  }

  public List<ProcessVariableNameResponseDto> getProcessVariableNames(
      String key, List<String> versions) {
    ProcessToQueryDto processToQuery = new ProcessToQueryDto();
    processToQuery.setProcessDefinitionKey(key);
    processToQuery.setProcessDefinitionVersions(versions);

    List<ProcessToQueryDto> processesToQuery = List.of(processToQuery);
    ProcessVariableNameRequestDto variableRequestDto =
        new ProcessVariableNameRequestDto(processesToQuery);

    return getProcessVariableNames(variableRequestDto);
  }

  public List<ProcessVariableNameResponseDto> getProcessVariableNames(
      final String key, final String version) {
    return getProcessVariableNames(key, ImmutableList.of(version));
  }

  public List<String> getProcessVariableValues(final ProcessVariableValueRequestDto requestDto) {
    return getRequestExecutor()
        .buildProcessVariableValuesRequest(requestDto)
        .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());
  }

  @SneakyThrows
  public VariableDto createMapJsonObjectVariableDto(final Map<String, Object> variable) {
    return createJsonObjectVariableDto(
        objectMapper.writeValueAsString(variable), "java.util.HashMap");
  }

  @SneakyThrows
  public VariableDto createListJsonObjectVariableDto(final List<Object> variable) {
    return createJsonObjectVariableDto(
        objectMapper.writeValueAsString(variable), "java.util.ArrayList");
  }

  @SneakyThrows
  public VariableDto createJsonObjectVariableDto(final String value, final String objectTypeName) {
    VariableDto objectVariableDto = new VariableDto();
    objectVariableDto.setType(VARIABLE_TYPE_OBJECT);
    objectVariableDto.setValue(value);
    VariableDto.ValueInfo info = new VariableDto.ValueInfo();
    info.setObjectTypeName(objectTypeName);
    info.setSerializationDataFormat(MediaType.APPLICATION_JSON);
    objectVariableDto.setValueInfo(info);
    return objectVariableDto;
  }

  @SneakyThrows
  public VariableDto createNativeJsonVariableDto(final Map<String, Object> variable) {
    return createNativeJsonVariableDto(objectMapper.writeValueAsString(variable));
  }

  @SneakyThrows
  public VariableDto createNativeJsonVariableDto(final List<Object> variable) {
    return createNativeJsonVariableDto(objectMapper.writeValueAsString(variable));
  }

  @SneakyThrows
  public VariableDto createNativeJsonVariableDto(final String value) {
    VariableDto nativeJsonVariableDto = new VariableDto();
    nativeJsonVariableDto.setType(VARIABLE_TYPE_JSON);
    nativeJsonVariableDto.setValue(value);
    return nativeJsonVariableDto;
  }

  public OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
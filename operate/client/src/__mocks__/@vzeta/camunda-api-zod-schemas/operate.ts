/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const GetProcessDefinitionStatisticsRequestBody = {};
export const GetProcessDefinitionStatisticsResponseBody = {};
export const ProcessDefinitionStatistic = {};
export const endpoints = {
  getProcessDefinitionStatistics: {
    method: 'POST',
    getUrl: ({
      processDefinitionKey,
      statisticName,
    }: {
      processDefinitionKey: string;
      statisticName: string;
    }) =>
      `/v2/process-definitions/${processDefinitionKey}/statistics/${statisticName}`,
  },
  getProcessInstanceStatistics: {
    method: 'GET',
    getUrl: ({
      processInstanceKey,
      statisticName,
    }: {
      processInstanceKey: string;
      statisticName: string;
    }) =>
      `/v2/process-instances/${processInstanceKey}/statistics/${statisticName}`,
  },
  getProcessSequenceFlows: {
    method: 'GET',
    getUrl: ({processInstanceKey}: {processInstanceKey: string}) =>
      `/v2/process-instances/${processInstanceKey}/sequence-flows`,
  },
  getProcessInstance: {
    method: 'GET',
    getUrl: ({processInstanceKey}: {processInstanceKey: string}) =>
      `/v2/process-instances/${processInstanceKey}`,
  },
  getProcessInstanceCallHierarchy: {
    method: 'GET',
    getUrl: ({processInstanceKey}: {processInstanceKey: string}) =>
      `/v2/process-instances/${processInstanceKey}/call-hierarchy`,
  },
};

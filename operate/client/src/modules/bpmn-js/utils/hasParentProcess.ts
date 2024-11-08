/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

/**
 * Recursive function which checks all parent flow nodes for the given bpmnProcessId.
 *
 * @returns true if bpmnProcessId is found, false otherwise.
 */
const hasParentProcess = ({
  flowNode,
  bpmnProcessId,
}: {
  flowNode?: BusinessObject;
  bpmnProcessId?: string;
}) => {
  if (flowNode === undefined) {
    throw Error('Please provide flowNode');
  }

  if (bpmnProcessId === undefined) {
    throw Error('Please provide bpmnProcessId');
  }

  if (flowNode.$parent === undefined) {
    return false;
  }

  if (flowNode.$parent.id === bpmnProcessId) {
    return true;
  }

  return hasParentProcess({
    flowNode: flowNode.$parent,
    bpmnProcessId,
  });
};
export {hasParentProcess};
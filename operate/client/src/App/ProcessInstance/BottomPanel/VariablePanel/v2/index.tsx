/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import {TabView} from 'modules/components/TabView';
import {processInstanceListenersStore} from 'modules/stores/processInstanceListeners';
import {useProcessInstancePageParams} from '../../../useProcessInstancePageParams';
import {InputOutputMappings} from '../InputOutputMappings';
import {VariablesContent as VariablesContentV2} from './VariablesContent';
import {Listeners} from '../Listeners';
import {WarningFilled} from '../styled';
import {init, startPolling} from 'modules/utils/variables';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useIsRootNodeSelected} from 'modules/hooks/flowNodeSelection';

const VariablePanel = observer(function VariablePanel() {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {data: processInstance} = useProcessInstance();
  const isRootNodeSelected = useIsRootNodeSelected();

  const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
  const flowNodeInstanceId =
    flowNodeSelectionStore.state.selection?.flowNodeInstanceId;

  const {
    listenersFailureCount,
    state,
    fetchListeners,
    reset,
    setListenerTabVisibility,
  } = processInstanceListenersStore;
  const {listenerTypeFilter} = state;

  const shouldUseFlowNodeId = !flowNodeInstanceId && flowNodeId;

  useEffect(() => {
    reset();
  }, [flowNodeId, flowNodeInstanceId, reset]);

  useEffect(() => {
    init(processInstance);

    return () => {
      variablesStore.reset();
    };
  }, [processInstance]);

  useEffect(() => {
    if (shouldUseFlowNodeId) {
      fetchListeners({
        fetchType: 'initial',
        processInstanceId: processInstanceId,
        payload: {
          flowNodeId,
          ...(listenerTypeFilter && {listenerTypeFilter}),
        },
      });
    } else if (flowNodeInstanceId) {
      fetchListeners({
        fetchType: 'initial',
        processInstanceId: processInstanceId,
        payload: {
          flowNodeInstanceId,
          ...(listenerTypeFilter && {listenerTypeFilter}),
        },
      });
    }
  }, [
    fetchListeners,
    processInstanceId,
    flowNodeId,
    flowNodeInstanceId,
    shouldUseFlowNodeId,
    listenerTypeFilter,
  ]);

  return (
    <TabView
      tabs={[
        {
          id: 'variables',
          label: 'Variables',
          content: <VariablesContentV2 />,
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(false);
            startPolling(processInstance);
            variablesStore.refreshVariables(processInstanceId);
          },
        },
        ...(isRootNodeSelected
          ? []
          : [
              {
                id: 'input-mappings',
                label: 'Input Mappings',
                content: <InputOutputMappings type="Input" />,
                onClick: () => {
                  setListenerTabVisibility(false);
                  return variablesStore.stopPolling;
                },
              },
              {
                id: 'output-mappings',
                label: 'Output Mappings',
                content: <InputOutputMappings type="Output" />,
                onClick: () => {
                  setListenerTabVisibility(false);
                  return variablesStore.stopPolling;
                },
              },
            ]),
        {
          id: 'listeners',
          testId: 'listeners-tab-button',
          ...(listenersFailureCount && {
            labelIcon: <WarningFilled />,
          }),
          label: 'Listeners',
          content: <Listeners />,
          removePadding: true,
          onClick: () => {
            setListenerTabVisibility(true);
            return variablesStore.stopPolling;
          },
        },
      ]}
      key={`tabview-${flowNodeId}-${flowNodeInstanceId}`}
    />
  );
});

export {VariablePanel};

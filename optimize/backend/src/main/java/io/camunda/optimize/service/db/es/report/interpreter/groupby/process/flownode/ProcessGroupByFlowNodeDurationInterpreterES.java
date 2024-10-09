/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_FLOW_NODE_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.util.DurationScriptUtilES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DurationAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByFlowNodeDurationInterpreterES
    extends AbstractGroupByFlowNodeInterpreterES {
  private final MinMaxStatsServiceES minMaxStatsService;
  @Getter private final DefinitionService definitionService;
  @Getter final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final DurationAggregationServiceES durationAggregationService;
  @Getter final ProcessViewInterpreterFacadeES viewInterpreter;

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_FLOW_NODE_DURATION);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return durationAggregationService
        .createLimitedGroupByScriptedEventDurationAggregation(
            boolQuery, context, getDurationScript())
        .map(durationAggregation -> createFilteredFlowNodeAggregation(context, durationAggregation))
        .orElse(Map.of());
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    compositeCommandResult.setGroupByKeyOfNumericType(true);
    compositeCommandResult.setDistributedByKeyOfNumericType(
        distributedByInterpreter.isKeyOfNumericType(context));
    getFilteredFlowNodesAggregation(response)
        .ifPresent(
            filteredFlowNodes -> {
              final List<CompositeCommandResult.GroupByResult> durationHistogramData =
                  durationAggregationService.mapGroupByDurationResults(
                      response, filteredFlowNodes.aggregations(), context);

              compositeCommandResult.setGroups(durationHistogramData);
            });
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    return Optional.of(retrieveMinMaxDurationStats(context, baseQuery));
  }

  private MinMaxStatDto retrieveMinMaxDurationStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    return minMaxStatsService.getScriptedMinMaxStats(
        baseQuery, getIndexNames(context), FLOW_NODE_INSTANCES, getDurationScript());
  }

  private Script getDurationScript() {
    return DurationScriptUtilES.getDurationScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
        FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION,
        FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE);
  }
}
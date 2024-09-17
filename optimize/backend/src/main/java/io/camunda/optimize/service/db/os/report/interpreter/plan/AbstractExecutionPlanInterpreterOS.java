/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.plan;

import static io.camunda.optimize.service.db.os.externalcode.client.dsl.UnitDSL.seconds;
import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.GroupByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.ViewInterpreterOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.ExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.interpreter.result.ResultInterpreter;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchRequest.Builder;
import org.opensearch.client.opensearch.core.SearchResponse;

@Slf4j
public abstract class AbstractExecutionPlanInterpreterOS<
        DATA extends SingleReportDataDto, PLAN extends ExecutionPlan>
    implements ExecutionPlanInterpreter<DATA, PLAN> {

  public CommandEvaluationResult<Object> interpret(ExecutionContext<DATA, PLAN> executionContext) {
    SearchResponse response;
    try {
      response = executeRequests(executionContext, createBaseQuerySearchRequest(executionContext));
    } catch (RuntimeException e) {
      if (isInstanceIndexNotFoundException(e)) {
        if (executionContext.getReportData().getDefinitions().size() > 1) {
          // If there are multiple data sources, we retry with the process instance index multi
          // alias to get a result
          log.info(
              "Could not evaluate report because at least one required instance index {} does not exist. Retrying with index "
                  + "multi alias",
              Arrays.asList(getIndexNames(executionContext)));
          List<String> indices = Arrays.asList(getMultiIndexAlias());
          Builder searchRequestBuilder =
              createBaseQuerySearchRequest(executionContext).index(indices);
          try {
            response = executeRequests(executionContext, searchRequestBuilder);
          } catch (RuntimeException ex) {
            if (isInstanceIndexNotFoundException(e)) {
              return returnEmptyResult(executionContext);
            } else {
              throw ex;
            }
          } catch (IOException ex) {
            throw e;
          }
        } else {
          return returnEmptyResult(executionContext);
        }
      } else {
        throw e;
      }
    } catch (IOException e) {
      String reason =
          String.format(
              "Could not evaluate %s report for definitions [%s]",
              executionContext.getPlan(), executionContext.getReportData().getDefinitions());
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return retrieveQueryResult(response, executionContext);
  }

  protected abstract GroupByInterpreterOS<DATA, PLAN> getGroupByInterpreter();

  protected abstract ViewInterpreterOS<DATA, PLAN> getViewInterpreter();

  protected abstract OptimizeOpenSearchClient getOsClient();

  protected abstract BoolQuery.Builder baseQueryBuilder(final ExecutionContext<DATA, PLAN> context);

  protected abstract String[] getIndexNames(final ExecutionContext<DATA, PLAN> context);

  protected abstract String[] getMultiIndexAlias();

  protected abstract BoolQuery.Builder unfilteredBaseQueryBuilder(
      final ExecutionContext<DATA, PLAN> reportData);

  private SearchRequest.Builder createBaseQuerySearchRequest(
      final ExecutionContext<DATA, PLAN> executionContext) {
    Query query =
        getGroupByInterpreter()
            .adjustQuery(baseQueryBuilder(executionContext), executionContext)
            .build()
            .toQuery();
    SearchRequest.Builder searchRequestBuilder =
        new SearchRequest.Builder()
            .index(Arrays.asList(getIndexNames(executionContext)))
            .query(query)
            .trackTotalHits(b -> b.enabled(true))
            .source(s -> s.fetch(false));
    // The null checks below are essential to prevent NPEs in integration tests
    executionContext
        .getPagination()
        .ifPresent(
            pagination -> {
              Optional.ofNullable(pagination.getOffset()).ifPresent(searchRequestBuilder::from);
              Optional.ofNullable(pagination.getLimit()).ifPresent(searchRequestBuilder::size);
            });
    searchRequestBuilder.aggregations(
        getGroupByInterpreter().createAggregation(query, executionContext));
    getGroupByInterpreter().adjustSearchRequest(searchRequestBuilder, query, executionContext);
    return searchRequestBuilder;
  }

  private SearchResponse<?> executeRequests(
      final ExecutionContext<DATA, PLAN> executionContext,
      final SearchRequest.Builder searchRequestBuilder)
      throws IOException {
    SearchResponse<?> response = executeSearch(executionContext, searchRequestBuilder);
    String[] indices = getIndexNames(executionContext);
    Query countQuery = unfilteredBaseQueryBuilder(executionContext).build().toQuery();
    executionContext.setUnfilteredTotalInstanceCount(getOsClient().count(indices, countQuery));
    return response;
  }

  private SearchResponse<?> executeSearch(
      final ExecutionContext<DATA, PLAN> executionContext,
      final SearchRequest.Builder searchRequestBuilder)
      throws IOException {
    PaginationDto paginationInfo = executionContext.getPagination().orElse(new PaginationDto());
    String errorMsg = "Failed to execute search request";
    if (paginationInfo instanceof PaginationScrollableDto scrollableDto) {
      String scrollId = scrollableDto.getScrollId();
      Integer timeout = scrollableDto.getScrollTimeout();
      if (scrollId != null && !scrollId.isEmpty()) {
        return getOsClient()
            .scroll(RequestDSL.scrollRequest(scrollId, seconds(timeout).time()), Object.class);
      } else {
        return getOsClient()
            .search(searchRequestBuilder.scroll(seconds(timeout)), Object.class, errorMsg);
      }
    } else {
      return getOsClient().search(searchRequestBuilder, Object.class, errorMsg);
    }
  }

  private CommandEvaluationResult<Object> returnEmptyResult(
      final ExecutionContext<DATA, PLAN> executionContext) {
    log.info("Could not evaluate report. Returning empty result instead");
    return ResultInterpreter.interpret(
        executionContext,
        new CompositeCommandResult(
            executionContext.getReportData(),
            getViewInterpreter().getViewProperty(executionContext),
            // the default number value differs across views, see the corresponding
            // createEmptyResult implementations
            // thus we refer to it here in order to create an appropriate empty result
            // see https://jira.camunda.com/browse/OPT-3336
            getViewInterpreter().createEmptyResult(executionContext).getViewMeasures().stream()
                .findFirst()
                .map(CompositeCommandResult.ViewMeasure::getValue)
                .orElse(null)));
  }

  private CommandEvaluationResult<Object> retrieveQueryResult(
      final SearchResponse<RawResult> response,
      final ExecutionContext<DATA, PLAN> executionContext) {
    final CompositeCommandResult result =
        getGroupByInterpreter().retrieveQueryResult(response, executionContext);
    final CommandEvaluationResult<Object> reportResult =
        ResultInterpreter.interpret(executionContext, result);
    reportResult.setInstanceCount(response.hits().total().value());
    reportResult.setInstanceCountWithoutFilters(executionContext.getUnfilteredTotalInstanceCount());
    executionContext
        .getPagination()
        .ifPresent(
            plainPagination -> {
              PaginationScrollableDto scrollablePagination =
                  PaginationScrollableDto.fromPaginationDto(plainPagination);
              scrollablePagination.setScrollId(response.scrollId());
              reportResult.setPagination(scrollablePagination);
            });
    return reportResult;
  }
}
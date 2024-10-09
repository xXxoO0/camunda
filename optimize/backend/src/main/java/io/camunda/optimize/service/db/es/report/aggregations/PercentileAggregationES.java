/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.aggregations;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.PercentilesAggregation.Builder;
import co.elastic.clients.elasticsearch._types.aggregations.TDigestPercentilesAggregate;
import co.elastic.clients.util.Pair;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.service.db.es.report.interpreter.util.AggregationResultMappingUtilES;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class PercentileAggregationES extends AggregationStrategyES<Builder> {

  private static final String PERCENTILE_AGGREGATION = "percentileAggregation";

  private Double percentileValue;

  @Override
  public Double getValueForAggregation(
      final String customIdentifier, final Map<String, Aggregate> aggs) {
    final TDigestPercentilesAggregate percentiles =
        aggs.get(
                createAggregationName(
                    customIdentifier, String.valueOf(percentileValue), PERCENTILE_AGGREGATION))
            .tdigestPercentiles();
    return AggregationResultMappingUtilES.mapToDoubleOrNull(percentiles, percentileValue);
  }

  @Override
  public Pair<String, Aggregation.Builder.ContainerBuilder> createAggregationBuilderForAggregation(
      final String customIdentifier, final Script script, final String... field) {
    final Aggregation.Builder builder = new Aggregation.Builder();
    return Pair.of(
        createAggregationName(
            customIdentifier, String.valueOf(percentileValue), PERCENTILE_AGGREGATION),
        builder.percentiles(
            a -> {
              a.script(script).percents(percentileValue);
              if (field != null && field.length != 0) {
                a.field(field[0]);
              }
              return a;
            }));
  }

  @Override
  public AggregationDto getAggregationType() {
    return new AggregationDto(AggregationType.PERCENTILE, percentileValue);
  }
}
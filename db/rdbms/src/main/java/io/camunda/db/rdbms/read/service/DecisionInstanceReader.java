/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.DecisionInstanceDbQuery;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper.DecisionInstanceSearchColumn;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedInput;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedOutput;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.result.DecisionInstanceQueryResultConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionInstanceReader extends AbstractEntityReader<DecisionInstanceEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(DecisionInstanceReader.class);

  private final DecisionInstanceMapper decisionInstanceMapper;

  public DecisionInstanceReader(final DecisionInstanceMapper decisionInstanceMapper) {
    super(DecisionInstanceSearchColumn::findByProperty);
    this.decisionInstanceMapper = decisionInstanceMapper;
  }

  public Optional<DecisionInstanceEntity> findOne(final long decisionInstanceKey) {
    LOG.trace("[RDBMS DB] Search for decision instance with key {}", decisionInstanceKey);
    final var result =
        search(
            DecisionInstanceQuery.of(
                b ->
                    b.filter(f -> f.decisionInstanceKeys(decisionInstanceKey))
                        .resultConfig(new DecisionInstanceQueryResultConfig(true, true))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }

  public SearchQueryResult<DecisionInstanceEntity> search(final DecisionInstanceQuery query) {
    final var dbSort =
        convertSort(query.sort(), DecisionInstanceSearchColumn.DECISION_INSTANCE_KEY);
    final var dbQuery =
        DecisionInstanceDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for process instance with filter {}", dbQuery);
    final var totalHits = decisionInstanceMapper.count(dbQuery);
    final var hits = enhanceEntities(decisionInstanceMapper.search(dbQuery), query.resultConfig());

    return new SearchQueryResult<>(totalHits.intValue(), hits, extractSortValues(hits, dbSort));
  }

  /**
   * Based on the result config, re batch-load here additional data (input, output values) with one
   * SQL each (if enabled).
   */
  private List<DecisionInstanceEntity> enhanceEntities(
      final List<DecisionInstanceEntity> intermediateResult,
      final DecisionInstanceQueryResultConfig resultConfig) {
    if (intermediateResult.isEmpty()) {
      return intermediateResult;
    }

    if (resultConfig == null) {
      return intermediateResult;
    }

    if (!resultConfig.includeEvaluatedInputs() && !resultConfig.includeEvaluatedOutputs()) {
      return intermediateResult;
    }

    final Map<Long, List<EvaluatedInput>> inputs = new HashMap<>();
    final Map<Long, List<EvaluatedOutput>> outputs = new HashMap<>();
    if (resultConfig.includeEvaluatedInputs()) {
      inputs.putAll(
          decisionInstanceMapper
              .loadInputs(intermediateResult.stream().map(DecisionInstanceEntity::key).toList())
              .stream()
              .collect(
                  Collectors.groupingBy(
                      DecisionInstanceDbModel.EvaluatedInput::decisionInstanceKey)));
    }
    if (resultConfig.includeEvaluatedOutputs()) {
      outputs.putAll(
          decisionInstanceMapper
              .loadOutputs(intermediateResult.stream().map(DecisionInstanceEntity::key).toList())
              .stream()
              .collect(
                  Collectors.groupingBy(
                      DecisionInstanceDbModel.EvaluatedOutput::decisionInstanceKey)));
    }

    return intermediateResult.stream()
        .map(
            entity ->
                entity.toBuilder()
                    .evaluatedInputs(mapInputList(inputs.getOrDefault(entity.key(), List.of())))
                    .evaluatedOutputs(mapOutputList(outputs.getOrDefault(entity.key(), List.of())))
                    .build())
        .toList();
  }

  private List<DecisionInstanceEntity.DecisionInstanceInputEntity> mapInputList(
      final List<DecisionInstanceDbModel.EvaluatedInput> inputList) {
    return inputList.stream()
        .map(
            i ->
                new DecisionInstanceEntity.DecisionInstanceInputEntity(i.id(), i.name(), i.value()))
        .toList();
  }

  private List<DecisionInstanceEntity.DecisionInstanceOutputEntity> mapOutputList(
      final List<DecisionInstanceDbModel.EvaluatedOutput> outputList) {
    return outputList.stream()
        .map(
            o ->
                new DecisionInstanceEntity.DecisionInstanceOutputEntity(
                    o.id(), o.name(), o.value(), o.ruleId(), o.ruleIndex()))
        .toList();
  }
}

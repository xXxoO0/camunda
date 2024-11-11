/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.decisioninstance;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures.createAndSaveRandomDecisionInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DecisionInstanceReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.DecisionDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.sort.DecisionInstanceSort;
import io.camunda.search.sort.DecisionInstanceSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class DecisionInstanceSortIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortByDecisionInstanceKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionInstanceKey().asc(),
        Comparator.comparing(DecisionInstanceEntity::key));
  }

  @TestTemplate
  public void shouldSortByDecisionInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionInstanceKey().desc(),
        Comparator.comparing(DecisionInstanceEntity::key).reversed());
  }

  @TestTemplate
  public void shouldSortByDecisionDefinitionIdAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionDefinitionId().asc(),
        Comparator.comparing(DecisionInstanceEntity::decisionId));
  }

  @TestTemplate
  public void shouldSortByDecisionDefinitionIdDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionDefinitionId().desc(),
        Comparator.comparing(DecisionInstanceEntity::decisionId).reversed());
  }

  @TestTemplate
  public void shouldSortByDecisionDefinitionName(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionDefinitionName().asc(),
        Comparator.comparing(DecisionInstanceEntity::decisionName));
  }

  @TestTemplate
  public void shouldSortByDecisionDefinitionVersion(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.decisionDefinitionVersion().asc(),
        Comparator.comparing(DecisionInstanceEntity::decisionVersion));
  }

  @TestTemplate
  public void shouldSortByEvaluationDate(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.evaluationDate().asc(),
        Comparator.comparing(DecisionInstanceEntity::evaluationDate));
  }

  @TestTemplate
  public void shouldSortByEvaluationDateDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.evaluationDate().desc(),
        Comparator.comparing(DecisionInstanceEntity::evaluationDate).reversed());
  }

  @TestTemplate
  public void shouldSortByState(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.state().asc(),
        Comparator.comparing(DecisionInstanceEntity::state));
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<DecisionInstanceSort>> sortBuilder,
      final Comparator<DecisionInstanceEntity> comparator) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceReader reader = rdbmsService.getDecisionInstanceReader();

    final var decisionDefinition =
        DecisionDefinitionFixtures.createAndSaveDecisionDefinition(rdbmsWriter, b -> b);
    final var processDefinitionKey = nextKey();
    createAndSaveRandomDecisionInstances(
        rdbmsWriter,
        b ->
            b.processDefinitionKey(processDefinitionKey)
                .decisionDefinitionId(decisionDefinition.decisionDefinitionId())
                .decisionDefinitionKey(decisionDefinition.decisionDefinitionKey()));

    final var searchResult =
        reader
            .search(
                new DecisionInstanceQuery(
                    new DecisionInstanceFilter.Builder()
                        .processDefinitionKeys(processDefinitionKey)
                        .build(),
                    DecisionInstanceSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b),
                    null))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class GroupTest {
  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateGroup() {
    final var name = UUID.randomUUID().toString();
    final var groupRecord = engine.group().newGroup(name).create();

    final var createdGroup = groupRecord.getValue();
    assertThat(createdGroup).hasName(name);
  }

  @Test
  public void shouldNotDuplicate() {
    // given
    final var name = UUID.randomUUID().toString();
    final var groupRecord = engine.group().newGroup(name).create();

    // when
    final var duplicatedGroupRecord = engine.group().newGroup(name).expectRejection().create();

    final var createdGroup = groupRecord.getValue();
    Assertions.assertThat(createdGroup).isNotNull().hasFieldOrPropertyWithValue("name", name);

    assertThat(duplicatedGroupRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create group with name '%s', but a group with this name already exists."
                .formatted(name));
  }

  @Test
  public void shouldUpdateGroup() {
    // given
    final var name = UUID.randomUUID().toString();
    final var groupRecord = engine.group().newGroup(name).create();

    // when
    final var groupKey = groupRecord.getKey();
    final var updatedName = name + "-updated";
    final var updatedGroupRecord =
        engine.group().updateGroup(groupKey).withName(updatedName).update();

    final var updatedGroup = updatedGroupRecord.getValue();
    assertThat(updatedGroup).hasName(updatedName);
  }

  @Test
  public void shouldRejectUpdatedIfNoGroupExists() {
    // when
    final var groupKey = 1L;
    final var updatedName = "yolo";
    final var updatedGroupRecord =
        engine.group().updateGroup(groupKey).withName(updatedName).expectRejection().update();

    // then
    assertThat(updatedGroupRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update group with key '%d', but a group with this key does not exist."
                .formatted(groupKey));
  }

  @Test
  public void shouldRejectUpdatedIfSameGroupExists() {
    // given
    final var groupName = "yolo";
    final var groupKey = engine.group().newGroup(groupName).create().getKey();

    // when
    final var updatedName = "yolo";
    final var updatedGroupRecord =
        engine.group().updateGroup(groupKey).withName(updatedName).expectRejection().update();

    // then
    assertThat(updatedGroupRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to update group with name '%s', but a group with this name already exists."
                .formatted(updatedName));
  }

  @Test
  public void shouldAddEntityToGroup() {
    // given
    final var userKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    final var name = UUID.randomUUID().toString();
    final var groupKey = engine.group().newGroup(name).create().getValue().getGroupKey();

    // when
    final var updatedGroup =
        engine
            .group()
            .addEntity(groupKey)
            .withEntityKey(userKey)
            .withEntityType(EntityType.USER)
            .add()
            .getValue();

    // then
    assertThat(updatedGroup).hasEntityKey(userKey).hasEntityType(EntityType.USER);
  }

  @Test
  public void shouldRejectIfGroupIsNotPresentWhileAddingEntity() {
    // when
    final var notPresentGroupKey = 1L;
    final var notPresentUpdateRecord =
        engine.group().addEntity(notPresentGroupKey).expectRejection().add();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update group with key '%d', but a group with this key does not exist."
                .formatted(notPresentGroupKey));
  }

  @Test
  public void shouldRejectIfEntityIsNotPresent() {
    // given
    final var name = UUID.randomUUID().toString();
    final var groupRecord = engine.group().newGroup(name).create();

    // when
    final var createdGroup = groupRecord.getValue();
    final var groupKey = createdGroup.getGroupKey();
    final var notPresentUpdateRecord =
        engine
            .group()
            .addEntity(groupKey)
            .withEntityKey(1L)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(createdGroup).hasName(name);
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to add an entity with key '%s' and type '%s' to group with key '%s', but the entity does not exist."
                .formatted(1L, EntityType.USER, groupKey));
  }

  @Test
  public void shouldRemoveEntityToGroup() {
    // given
    final var userKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    final var name = UUID.randomUUID().toString();
    final var groupKey = engine.group().newGroup(name).create().getValue().getGroupKey();
    engine.group().addEntity(groupKey).withEntityKey(userKey).withEntityType(EntityType.USER).add();

    // when
    final var groupWithRemovedEntity =
        engine
            .group()
            .removeEntity(groupKey)
            .withEntityKey(userKey)
            .withEntityType(EntityType.USER)
            .remove()
            .getValue();

    // then
    assertThat(groupWithRemovedEntity)
        .hasGroupKey(groupKey)
        .hasEntityKey(userKey)
        .hasEntityType(EntityType.USER);
  }

  @Test
  public void shouldRejectIfGroupIsNotPresentEntityRemoval() {
    // when
    final var notPresentGroupKey = 1L;
    final var notPresentUpdateRecord =
        engine.group().addEntity(notPresentGroupKey).expectRejection().add();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update group with key '%d', but a group with this key does not exist."
                .formatted(notPresentGroupKey));
  }

  @Test
  public void shouldRejectIfEntityIsNotPresentEntityRemoval() {
    // given
    final var name = UUID.randomUUID().toString();
    final var groupRecord = engine.group().newGroup(name).create();

    // when
    final var createdGroup = groupRecord.getValue();
    final var groupKey = createdGroup.getGroupKey();
    final var notPresentUpdateRecord =
        engine
            .group()
            .removeEntity(groupKey)
            .withEntityKey(1L)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .remove();

    // then
    assertThat(createdGroup).hasName(name);
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove an entity with key '%s' and type '%s' from group with key '%s', but the entity does not exist."
                .formatted(1L, EntityType.USER, groupKey));
  }

  @Test
  public void shouldDeleteGroup() {
    // given
    final var name = UUID.randomUUID().toString();
    final var groupKey = engine.group().newGroup(name).create().getValue().getGroupKey();
    engine
        .authorization()
        .permission()
        .withOwnerKey(groupKey)
        .withOwnerType(AuthorizationOwnerType.ROLE)
        .withResourceType(AuthorizationResourceType.ROLE)
        .add();

    // when
    final var deletedGroup = engine.group().deleteGroup(groupKey).delete().getValue();

    // then
    assertThat(deletedGroup).hasGroupKey(groupKey);
  }

  @Test
  public void shouldRejectIfGroupIsNotPresentOnDeletion() {
    // when
    final var notPresentGroupKey = 1L;
    final var notPresentUpdateRecord =
        engine.group().deleteGroup(notPresentGroupKey).expectRejection().delete();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete group with key '%s', but a group with this key does not exist."
                .formatted(notPresentGroupKey));
  }
}

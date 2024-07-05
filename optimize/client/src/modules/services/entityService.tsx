/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ChartColumn, Dashboard, Folder} from '@carbon/icons-react';

import {get, post} from 'request';
import {EntityListEntity, GenericReport} from 'types';
import {track} from 'tracking';

export async function loadReports(collection?: string | null): Promise<GenericReport[]> {
  let url = 'api/report';
  if (collection) {
    url = `api/collection/${collection}/reports`;
  }
  const response = await get(url);
  return await response.json();
}

export async function loadEntities<T extends Record<string, unknown>>(
  sortBy?: string,
  sortOrder?: string
): Promise<EntityListEntity<T>[]> {
  const params: Record<string, unknown> = {};
  if (sortBy && sortOrder) {
    params.sortBy = sortBy;
    params.sortOrder = sortOrder;
  }

  const response = await get('api/entities', params);
  return await response.json();
}

export function createEventName(action: string, entityType: string) {
  const type = getEntityType(entityType);
  return action + type.charAt(0).toUpperCase() + type.slice(1);
}

function getEntityType(entityType: string) {
  return entityType === 'dashboard/instant' ? 'instantPreviewDashboard' : entityType;
}

export async function copyEntity(
  type: string,
  id: string,
  name?: string | null,
  collectionId?: string | null
): Promise<string> {
  const query: Record<string, unknown> = {};

  if (name) {
    query.name = name;
  }

  if (collectionId || collectionId === null) {
    query.collectionId = collectionId;
  }

  const response = await post(`api/${type}/${id}/copy`, undefined, {query});
  const json = await response.json();

  return json.id as string;
}

export async function createEntity(
  type: string,
  initialValues = {},
  context?: string
): Promise<GenericReport> {
  const response = await post('api/' + type, initialValues);
  const json = await response.json();
  track(createEventName('create', type), {entityId: json.id, context});
  return json.id;
}

export function getEntityIcon(type: 'report' | 'dashboard' | 'collection') {
  switch (type) {
    case 'collection':
      return <Folder />;
    case 'dashboard':
      return <Dashboard />;
    case 'report':
      return <ChartColumn />;
  }
}
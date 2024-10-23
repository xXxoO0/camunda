/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.impl.search.filter;

import io.camunda.zeebe.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.ProcessDefinitionFilterRequest;

public class ProcessDefinitionFilterImpl
    extends TypedSearchRequestPropertyProvider<ProcessDefinitionFilterRequest>
    implements ProcessDefinitionFilter {

  private final ProcessDefinitionFilterRequest filter;

  public ProcessDefinitionFilterImpl() {
    filter = new ProcessDefinitionFilterRequest();
  }

  @Override
  public ProcessDefinitionFilter processDefinitionKey(final long processDefinitionKey) {
    filter.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  @Override
  public ProcessDefinitionFilter name(final String name) {
    filter.setName(name);
    return this;
  }

  @Override
  public ProcessDefinitionFilter resourceName(final String resourceName) {
    filter.setResourceName(resourceName);
    return this;
  }

  @Override
  public ProcessDefinitionFilter version(final int version) {
    filter.setVersion(version);
    return this;
  }

  @Override
  public ProcessDefinitionFilter versionTag(final String versionTag) {
    filter.setVersionTag(versionTag);
    return this;
  }

  @Override
  public ProcessDefinitionFilter processDefinitionId(final String processDefinitionId) {
    filter.setProcessDefinitionId(processDefinitionId);
    return this;
  }

  @Override
  public ProcessDefinitionFilter tenantId(final String tenantId) {
    filter.setTenantId(tenantId);
    return this;
  }

  @Override
  protected ProcessDefinitionFilterRequest getSearchRequestProperty() {
    return filter;
  }
}
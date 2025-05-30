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
package io.camunda.client.role;

import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

public class AssignRoleToGroupTest extends ClientRestTest {

  public static final String ROLE_ID = "roleId";
  public static final String GROUP_ID = "groupId";

  @Test
  void shouldAssignRoleToGroup() {
    // when
    client.newAssignRoleToGroupCommand().roleId(ROLE_ID).groupId(GROUP_ID).send().join();

    // then
    final String requestPath = RestGatewayService.getLastRequest().getUrl();
    assertThat(requestPath).isEqualTo(REST_API_PATH + "/roles/" + ROLE_ID + "/groups/" + GROUP_ID);
  }

  @Test
  void shouldRaiseExceptionOnNullRoleId() {
    // when / then
    assertThatThrownBy(
            () -> client.newAssignRoleToGroupCommand().roleId(null).groupId(GROUP_ID).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("role must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyRoleId() {
    // when / then
    assertThatThrownBy(
            () -> client.newAssignRoleToGroupCommand().roleId("").groupId(GROUP_ID).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("role must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNullGroupId() {
    // when / then
    assertThatThrownBy(
            () -> client.newAssignRoleToGroupCommand().roleId(ROLE_ID).groupId(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyGroupId() {
    // when / then
    assertThatThrownBy(
            () -> client.newAssignRoleToGroupCommand().roleId(ROLE_ID).groupId("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }
}

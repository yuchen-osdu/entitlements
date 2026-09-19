/*
 * Copyright 2020-2026 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.entitlements.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.entitlements.Group;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupType;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupsInPartitionResponse;

public class ListAllPartitionGroupsTest extends BaseEntitlementsAcceptanceTest {

    @Test
    void shouldReturnOnlyServiceGroupsByGroupType() {
        assertGroupsStartWithType(GroupType.SERVICE);
    }

    @Test
    void shouldReturnOnlyDataGroupsByGroupType() {
        assertGroupsStartWithType(GroupType.DATA);
    }

    @Test
    void shouldReturnOnlyUserGroupsByGroupType() {
        assertGroupsStartWithType(GroupType.USER);
    }

    @Test
    void shouldReturnGroupsWithinLimitIfLimitParamPresent() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("limit", "10");
        params.put("type", "service");
        GroupsInPartitionResponse groups =
            entitlementsClient.listAllGroupsInPartition(params).body();
        assertEquals(10, groups.groups().length);
    }

    @Test
    void shouldReturnBadRequestIfTypeParamNotPresent() {
        assertBadRequest(Map.of("limit", "10"));
    }

    @Test
    void shouldReturnBadRequestWhenMalformedLimit() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("limit", "-1");
        params.put("type", "service");
        assertBadRequest(params);
    }

    @Test
    void shouldReturnBadRequestWhenMalformedGroupType() {
        assertBadRequest(Map.of("type", "NONEXISTENT"));
    }

    private void assertGroupsStartWithType(GroupType groupType) {
        String type = groupType.toString().toLowerCase();
        GroupsInPartitionResponse groups =
            entitlementsClient.listAllGroupsInPartition(Map.of("type", type)).body();
        assertTrue(Arrays.stream(groups.groups()).map(Group::email).allMatch(email -> email.startsWith(type)),
            "All returned groups should start with " + type);
    }

    private void assertBadRequest(Map<String, String> params) {
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.listAllGroupsInPartition(params));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());
    }
}

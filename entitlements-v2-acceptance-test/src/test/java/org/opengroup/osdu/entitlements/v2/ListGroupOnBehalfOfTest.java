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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.entitlements.Group;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupReference;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupsResponse;

public class ListGroupOnBehalfOfTest extends BaseEntitlementsAcceptanceTest {

    private final long currentTime = System.currentTimeMillis();

    @Test
    void shouldReturnAllGroupsThatGivenMemberBelongsTo() {
        List<Group> createdGroups = setup(MEMBER_EMAIL);

        GroupsResponse groups =
            entitlementsClient.listMemberGroups(MEMBER_EMAIL, DEFAULT_USER, Map.of("type", "NONE")).body();

        assertEquals(MEMBER_EMAIL.toLowerCase(), groups.desId());
        assertEquals(MEMBER_EMAIL.toLowerCase(), groups.memberEmail());
        assertFoundGroups(groups, createdGroups);
    }

    @Test
    void should200ForGetGroupsOnBehalfOfWithRoleEnabled() {
        List<Group> createdGroups = setup(MEMBER_EMAIL);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("type", "NONE");
        params.put("roleRequired", "true");

        GroupsResponse groups =
            entitlementsClient.listMemberGroups(MEMBER_EMAIL, DEFAULT_USER, params).body();

        assertEquals(MEMBER_EMAIL.toLowerCase(), groups.desId());
        assertEquals(MEMBER_EMAIL.toLowerCase(), groups.memberEmail());
        assertFoundGroups(groups, createdGroups);
        assertTrue(Arrays.stream(groups.groups())
            .allMatch(group -> group.roleName() != null && !group.roleName().isEmpty()));
    }

    @Test
    void shouldReturn400WhenGroupsTypeIsMissed() {
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.listMemberGroups(MEMBER_EMAIL, DEFAULT_USER, Map.of()));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void shouldReturn400WhenGroupsTypeIsUnknown() {
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.listMemberGroups(MEMBER_EMAIL, DEFAULT_USER, Map.of("type", "test")));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestWhenMakingHttpRequestWithInvalidUrl() {
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.listMemberGroups("%3B", DEFAULT_USER, Map.of("type", "NONE")));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());
    }

    private void assertFoundGroups(GroupsResponse groups, List<Group> createdGroups) {
        List<String> createdEmails = createdGroups.stream().map(Group::email).sorted(String::compareTo).toList();
        List<String> foundGroups = Arrays.stream(groups.groups())
            .map(GroupReference::email)
            .filter(createdEmails::contains)
            .sorted(String::compareTo)
            .collect(Collectors.toList());
        assertEquals(3, foundGroups.size());
        assertEquals(createdEmails.get(0), foundGroups.get(0));
        assertEquals(createdEmails.get(1), foundGroups.get(1));
        assertEquals(createdEmails.get(2), foundGroups.get(2));
    }

    private List<Group> setup(String memberEmail) {
        List<Group> groups = new ArrayList<>();
        groups.add(entitlementsClient.createGroup("group1-" + currentTime, "desc", DEFAULT_USER).body());
        groups.add(entitlementsClient.createGroup("group2-" + currentTime, "desc", DEFAULT_USER).body());
        groups.add(entitlementsClient.createGroup("group3-" + currentTime, "desc", DEFAULT_USER).body());
        for (Group group : groups) {
            entitlementsClient.addMemberToGroup(group.email(), memberEmail, "MEMBER", DEFAULT_USER);
        }
        return groups;
    }
}

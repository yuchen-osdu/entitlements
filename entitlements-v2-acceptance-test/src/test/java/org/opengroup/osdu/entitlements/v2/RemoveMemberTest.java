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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.entitlements.Group;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupMember;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupReference;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupsResponse;

public class RemoveMemberTest extends BaseEntitlementsAcceptanceTest {

    private final long currentTime = System.currentTimeMillis();

    /**
     * Create a group + child group, add child as member, remove it, verify it is gone and the
     * child group still exists.
     */
    @Test
    void shouldSuccessfullyRemoveMember() {
        Group group = entitlementsClient.createGroup("group-" + currentTime, "desc", DEFAULT_USER).body();
        Group child = entitlementsClient.createGroup("child-group-name" + currentTime, "desc", DEFAULT_USER).body();
        entitlementsClient.addMemberToGroup(group.email(), child.email(), "MEMBER", DEFAULT_USER);

        entitlementsClient.removeMemberFromGroup(group.email(), child.email(), DEFAULT_USER);

        GroupMember[] members = entitlementsClient.listGroupMembers(group.email(), DEFAULT_USER).body().members();
        assertFalse(Arrays.stream(members).anyMatch(m -> m.email().equals(child.email())));
        // child group still exists
        assertEquals(HttpStatus.SC_OK, entitlementsClient.listGroupMembers(child.email(), DEFAULT_USER).statusCode());
    }

    /**
     * A user that belongs to other groups cannot be removed from the elementary data-partition
     * users group; removal must be done via the delete-member endpoint instead.
     */
    @Test
    void shouldFailToRemoveMemberFromElementaryDPGroupIfUserIsMemberOfOtherGroups() {
        String userName = memberToBeDeleted(currentTime);
        String elementaryUsersGroup = groupEmail("users");

        Group group = entitlementsClient.createGroup("group-" + currentTime, "desc", DEFAULT_USER).body();
        entitlementsClient.addMemberToGroup(group.email(), userName, "MEMBER", DEFAULT_USER);
        entitlementsClient.addMemberToGroup(elementaryUsersGroup, userName, "MEMBER", DEFAULT_USER);

        // removal from the elementary data-partition users group is rejected with 400
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.removeMemberFromGroup(elementaryUsersGroup, userName, DEFAULT_USER));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());

        // delete-member removes the user from every group
        entitlementsClient.deleteMember(userName, DEFAULT_USER);

        GroupsResponse groups =
            entitlementsClient.listMemberGroups(userName, DEFAULT_USER, Map.of("type", "NONE")).body();
        GroupReference[] memberGroups = groups.groups();
        assertTrue(memberGroups == null || memberGroups.length == 0);
    }
}

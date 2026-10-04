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
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.entitlements.Group;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupMember;

public class GetMembersTest extends BaseEntitlementsAcceptanceTest {

    private final long currentTime = System.currentTimeMillis();

    @Test
    void shouldSuccessfullyListMember() {
        Group group = entitlementsClient.createGroup("group-" + currentTime, "desc").body();
        Group c1 = entitlementsClient.createGroup("child-group-name-1" + currentTime, "desc").body();
        Group c2 = entitlementsClient.createGroup("child-group-name-2" + currentTime, "desc").body();
        Group c3 = entitlementsClient.createGroup("child-group-name-3" + currentTime, "desc").body();

        entitlementsClient.addMemberToGroup(group.email(), c1.email(), "MEMBER");
        entitlementsClient.addMemberToGroup(group.email(), c2.email(), "MEMBER");
        entitlementsClient.addMemberToGroup(group.email(), c3.email(), "MEMBER");

        GroupMember[] members = entitlementsClient.listGroupMembers(group.email()).body().members();
        assertTrue(contains(members, c1.email()));
        assertTrue(contains(members, c2.email()));
        assertTrue(contains(members, c3.email()));
    }

    @Test
    void shouldReturnBadRequestWhenMakingHttpRequestWithInvalidUrl() {
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.listGroupMembers("%3B"));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());
    }

    private boolean contains(GroupMember[] members, String email) {
        return Arrays.stream(members).anyMatch(m -> m.email().equals(email));
    }
}

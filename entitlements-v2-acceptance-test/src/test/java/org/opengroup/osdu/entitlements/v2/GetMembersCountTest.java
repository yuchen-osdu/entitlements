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

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.entitlements.Group;
import org.opengroup.osdu.core.test.client.model.entitlements.MembersCountResponse;

public class GetMembersCountTest extends BaseEntitlementsAcceptanceTest {

    private final long currentTime = System.currentTimeMillis();

    @Test
    void shouldSuccessfullyCountMembers() {
        Group group = entitlementsClient.createGroup("group-" + currentTime, "desc", DEFAULT_USER).body();
        Group child1 = entitlementsClient.createGroup("child-group-name-1" + currentTime, "desc", DEFAULT_USER).body();
        Group child2 = entitlementsClient.createGroup("child-group-name-2" + currentTime, "desc", DEFAULT_USER).body();
        Group child3 = entitlementsClient.createGroup("child-group-name-3" + currentTime, "desc", DEFAULT_USER).body();

        entitlementsClient.addMemberToGroup(group.email(), child1.email(), "MEMBER", DEFAULT_USER);
        entitlementsClient.addMemberToGroup(group.email(), child2.email(), "MEMBER", DEFAULT_USER);
        entitlementsClient.addMemberToGroup(group.email(), child3.email(), "MEMBER", DEFAULT_USER);

        MembersCountResponse count = entitlementsClient.getMembersCount(group.email(), DEFAULT_USER).body();
        assertEquals(group.email(), count.groupEmail());
        // creator (OWNER) + 3 child groups
        assertEquals(4, count.membersCount());
    }

    @Test
    void shouldReturnBadRequestWhenMakingHttpRequestWithInvalidUrl() {
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.getMembersCount("%3B", DEFAULT_USER));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());
    }
}

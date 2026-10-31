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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.entitlements.Group;
import org.opengroup.osdu.core.test.client.model.entitlements.UpdateGroupOperation;
import org.opengroup.osdu.core.test.client.model.entitlements.UpdateGroupResponse;

public class UpdateGroupTest extends BaseEntitlementsAcceptanceTest {

    private final long currentTime = System.currentTimeMillis();

    @Test
    void shouldRenameGroupSuccessfully() {
        String oldGroupName = "oldGroupName-" + currentTime;
        String newGroupName = "newGroupName-" + currentTime;
        Group created = entitlementsClient.createGroup(oldGroupName, "desc").body();

        UpdateGroupResponse updated = entitlementsClient.updateGroupAttributes(created.email(),
            List.of(new UpdateGroupOperation("replace", "/name", List.of(newGroupName)))).body();

        assertEquals(newGroupName.toLowerCase(), updated.name());
        assertEquals(groupEmail(newGroupName).toLowerCase(), updated.email());
        // the rename produces a new group email; track the new one for teardown
        entitlementsClient.deleteGroup(updated.email());
    }

    @Test
    void shouldUpdateAppIdsSuccessfully() {
        String groupName = "groupName-" + currentTime;
        Set<String> newAppIds = new HashSet<>(List.of("app1", "app2"));
        Group created = entitlementsClient.createGroup(groupName, "desc").body();

        UpdateGroupResponse updated = entitlementsClient.updateGroupAttributes(created.email(),
            List.of(new UpdateGroupOperation("replace", "/appIds", new ArrayList<>(newAppIds)))).body();

        assertEquals(groupName.toLowerCase(), updated.name());
        assertEquals(groupEmail(groupName).toLowerCase(), updated.email());
        assertEquals(newAppIds, new HashSet<>(updated.appIds()));
    }

    @Test
    void shouldReturnBadRequestWhenMakingHttpRequestWithoutValidUrl() {
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.updateGroupAttributes("%25",
                List.of(new UpdateGroupOperation("replace", "/name", List.of("newGroupName")))));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());
    }
}

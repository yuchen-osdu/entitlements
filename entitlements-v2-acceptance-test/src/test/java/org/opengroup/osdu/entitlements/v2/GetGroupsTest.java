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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupsResponse;

public class GetGroupsTest extends BaseEntitlementsAcceptanceTest {

    @Test
    void shouldReturn200WhenMakingValidGetGroupsRequest() {
        GroupsResponse response = entitlementsClient.listGroups().body();
        // desId and memberEmail both identify the authenticated caller and must match.
        assertNotNull(response.principal().orElse(null));
        assertEquals(response.desId(), response.memberEmail());
    }

    @Test
    void should200ForGetGroupsWithRoleEnabled() {
        GroupsResponse response =
            entitlementsClient.listGroups(Map.of("roleRequired", "true")).body();
        assertEquals(response.desId(), response.memberEmail());
        // every returned group must carry role information
        assertTrue(Arrays.stream(response.groups())
            .allMatch(group -> group.roleName() != null && !group.roleName().isEmpty()));
    }
}

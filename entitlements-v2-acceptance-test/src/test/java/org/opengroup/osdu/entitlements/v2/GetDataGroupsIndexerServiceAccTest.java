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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupReference;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupsResponse;
import org.opengroup.osdu.core.test.config.EnvLoader;

public class GetDataGroupsIndexerServiceAccTest extends BaseEntitlementsAcceptanceTest {

    private final String indexerServiceAccountEmail = EnvLoader.get("INDEXER_SERVICE_ACCOUNT_EMAIL");
    private final boolean dataRootGroupHierarchyEnabled =
        Boolean.parseBoolean(EnvLoader.get("DATA_ROOT_GROUP_HIERARCHY_ENABLED"));

    @Test
    void shouldReturnCreatedDataGroupForIndexerServiceAcc() {
        assumeTrue(dataRootGroupHierarchyEnabled);
        String dataGroupName = "data.indexer.test.group";
        String dataGroupEmail = groupEmail(dataGroupName);

        if (isDataGroupAbsent(dataGroupEmail)) {
            entitlementsClient.createGroup(dataGroupName, "desc", DEFAULT_USER);
        }

        assertTrue(parentGroupEmails(indexerServiceAccountEmail).anyMatch(dataGroupEmail::equals));

        entitlementsClient.deleteGroup(dataGroupEmail, DEFAULT_USER);

        assertFalse(parentGroupEmails(indexerServiceAccountEmail).anyMatch(dataGroupEmail::equals));
    }

    private boolean isDataGroupAbsent(String dataGroupEmail) {
        GroupsResponse groups = entitlementsClient.listGroups(DEFAULT_USER).body();
        return Arrays.stream(groups.groups()).map(GroupReference::email).noneMatch(dataGroupEmail::equals);
    }

    private java.util.stream.Stream<String> parentGroupEmails(String memberEmail) {
        GroupsResponse groups =
            entitlementsClient.listMemberGroups(memberEmail, DEFAULT_USER, Map.of("type", "data")).body();
        return Arrays.stream(groups.groups()).map(GroupReference::email);
    }
}

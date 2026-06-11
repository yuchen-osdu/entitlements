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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Properties;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.config.EnvLoader;
import org.opengroup.osdu.core.test.client.model.entitlements.CreateGroupRequest;
import org.opengroup.osdu.core.test.client.model.entitlements.Group;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupMember;

public class CreateGroupTest extends BaseEntitlementsAcceptanceTest {

    private final long currentTime = System.currentTimeMillis();

    @Test
    void shouldAddDataRootAsMemberOfNewDataGroup() throws Exception {
        String groupName = "data.groupName-" + currentTime;

        HttpResponse<Group> response = entitlementsClient.createGroup(groupName, "desc", DEFAULT_USER);
        assertEquals(HttpStatus.SC_CREATED, response.statusCode());
        Group createdGroup = response.body();
        assertEquals(groupName.toLowerCase(), createdGroup.name());
        assertEquals("desc", createdGroup.description());
        assertNotNull(createdGroup.email());

        verifyRootGroupMembership(createdGroup);
    }

    @Test
    void shouldCreateGroupOnlyOneTimeSuccessfully() throws Exception {
        String groupName = "groupName-" + currentTime;

        HttpResponse<Group> response = entitlementsClient.createGroup(groupName, "desc", DEFAULT_USER);
        assertEquals(HttpStatus.SC_CREATED, response.statusCode());
        Group createdGroup = response.body();
        assertEquals(groupName.toLowerCase(), createdGroup.name());
        assertEquals("desc", createdGroup.description());
        assertNotNull(createdGroup.email());

        verifyConflictException(groupName);
    }

    private void verifyConflictException(String groupName) {
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.createGroup(new CreateGroupRequest(groupName, "desc"), DEFAULT_USER));
        assertEquals(HttpStatus.SC_CONFLICT, exception.getStatusCode());
        // os-core-test 0.1.6 stores the raw error body in AppError.message and uses a generic
        // reason, so assert on status + message content rather than the parsed reason field.
        assertTrue(exception.getError().getMessage().contains("This group already exists"));
    }

    private void verifyRootGroupMembership(Group createdGroup) {
        String createdEmail = createdGroup.email();
        String suffix = createdEmail.split("@")[1];
        String rootEmail = String.format("users.data.root@%s", suffix);

        boolean rootIsMemberOfDataGroup = isSecondGroupMemberOfFirst(createdEmail, rootEmail);
        boolean dataGroupIsMemberOfRoot = isSecondGroupMemberOfFirst(rootEmail, createdEmail);

        if (isFeatureFlagEnabled("disable-data-root-group-hierarchy")) {
            // Feature flag ON: no explicit hierarchy links should exist.
            assertFalse(rootIsMemberOfDataGroup,
                "With disable-data-root-group-hierarchy enabled, root group should NOT be a member of the new data group");
            assertFalse(dataGroupIsMemberOfRoot,
                "With disable-data-root-group-hierarchy enabled, new data group should NOT be a member of the root group");
        } else {
            // Feature flag OFF: legacy behaviour with explicit hierarchy.
            assertFalse(dataGroupIsMemberOfRoot,
                "Ensure that the newly created data group is NOT a member of the root group");
            assertTrue(rootIsMemberOfDataGroup,
                "Ensure that the root group is a member of the newly created data group");
        }
    }

    private boolean isSecondGroupMemberOfFirst(String firstEmail, String secondEmail) {
        HttpResponse<org.opengroup.osdu.core.test.client.model.entitlements.GroupMembersResponse> response =
            entitlementsClient.listGroupMembers(firstEmail, DEFAULT_USER);
        for (GroupMember member : response.body().members()) {
            if (member.email().equalsIgnoreCase(secondEmail)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Domain-only feature-flag lookup (no os-core-test equivalent). Reads {@code test.properties}
     * first, then falls back to the {@code DATA_ROOT_GROUP_HIERARCHY_ENABLED} environment variable.
     */
    private boolean isFeatureFlagEnabled(String flagName) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("test.properties")) {
            if (input != null) {
                Properties properties = new Properties();
                properties.load(input);
                String value = properties.getProperty(flagName);
                if (value != null) {
                    return Boolean.parseBoolean(value);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to read feature flag from test.properties: " + e.getMessage());
        }
        if ("disable-data-root-group-hierarchy".equals(flagName)) {
            String envValue = EnvLoader.get("DATA_ROOT_GROUP_HIERARCHY_ENABLED");
            if (envValue != null) {
                return !Boolean.parseBoolean(envValue);
            }
        }
        return false;
    }
}

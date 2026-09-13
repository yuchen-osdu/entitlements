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
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.model.entitlements.Group;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupMember;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupReference;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupsResponse;

public class ImpersonationTest extends BaseEntitlementsAcceptanceTest {

    @Test
    void shouldGetOkImpersonatedGet() throws Exception {
        String memberEmail = "impersonatetestmember@test.com";
        ensureMember(groupEmail("users"), memberEmail);
        ensureMember(groupEmail("users.datalake.impersonation"), memberEmail);
        ensureMember(groupEmail("service.entitlements.user"), memberEmail);

        // control random group with the member added
        String groupName = "groupname-" + System.currentTimeMillis();
        Group group = entitlementsClient.createGroup(groupName, "desc").body();
        entitlementsClient.addMemberToGroup(group.email(), memberEmail, "MEMBER");

        HttpResponse<GroupsResponse> response = entitlementsClient.listGroupsOnBehalfOf(memberEmail);
        assertEquals(HttpStatus.SC_OK, response.statusCode());
        GroupsResponse groups = response.body();
        assertEquals(memberEmail, groups.memberEmail());
        assertEquals(memberEmail, groups.desId());
        boolean delegationGroupPresent = Arrays.stream(groups.groups())
            .map(GroupReference::name)
            .anyMatch(groupName::equalsIgnoreCase);
        assertTrue(delegationGroupPresent);

        entitlementsClient.removeMemberFromGroup(group.email(), memberEmail);
    }

    @Test
    void shouldFailIfNoImpersonationGroup() throws Exception {
        String memberEmail = "impersonationtestmemberwithoutgroup@test.com";
        ensureMember(groupEmail("users"), memberEmail);
        String impersonationGroup = groupEmail("users.datalake.impersonation");
        if (isMember(impersonationGroup, memberEmail)) {
            entitlementsClient.removeMemberFromGroup(impersonationGroup, memberEmail);
        }

        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.listGroupsOnBehalfOf(memberEmail));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void shouldFailIfNoDelegationGroup() {
        ClientException exception = assertThrows(ClientException.class,
            () -> noAccessEntitlementsClient.listGroupsOnBehalfOf("no.matter@user.id"));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void shouldFailIfTryToImpersonateTenantServiceAccount() {
        String tenantServiceAccount = partitionClient.getServiceAccount(partitionId());
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.listGroupsOnBehalfOf(tenantServiceAccount));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getStatusCode());
    }

    private void ensureMember(String groupEmail, String memberEmail) {
        if (!isMember(groupEmail, memberEmail)) {
            entitlementsClient.addMemberToGroup(groupEmail, memberEmail, "MEMBER");
        }
    }

    private boolean isMember(String groupEmail, String memberEmail) {
        GroupMember[] members = entitlementsClient.listGroupMembers(groupEmail).body().members();
        return Arrays.stream(members).anyMatch(m -> m.email().equals(memberEmail));
    }
}
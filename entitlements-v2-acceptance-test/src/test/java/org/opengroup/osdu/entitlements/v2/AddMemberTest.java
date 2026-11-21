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

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.model.entitlements.AddMemberRequest;
import org.opengroup.osdu.core.test.client.model.entitlements.Group;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupMember;
import org.opengroup.osdu.core.test.client.model.entitlements.GroupMembersResponse;

public class AddMemberTest extends BaseEntitlementsAcceptanceTest {

    private final long currentTime = System.currentTimeMillis();

    @Test
    void shouldAddMemberSuccessfully() throws Exception {
        String groupName = "groupName-" + currentTime;
        String childGroupName = "child-groupName-" + currentTime;

        Group group = entitlementsClient.createGroup(groupName, "desc").body();

        entitlementsClient.addMemberToGroup(group.email(), OWNER_EMAIL, "OWNER");
        entitlementsClient.addMemberToGroup(group.email(), MEMBER_EMAIL, "MEMBER");

        verifyConflictError(group.email(), MEMBER_EMAIL, "MEMBER");

        Group childGroup = entitlementsClient.createGroup(childGroupName, "desc").body();
        entitlementsClient.addMemberToGroup(group.email(), childGroup.email(), "MEMBER");

        HttpResponse<GroupMembersResponse> response =
            entitlementsClient.listGroupMembers(group.email());
        assertEquals(HttpStatus.SC_OK, response.statusCode());
        GroupMembersResponse members = response.body();

        // creator (auto OWNER) + added OWNER + added MEMBER + child group MEMBER
        assertEquals(4, members.members().length);
        verifyMemberInResponse(members, "MEMBER", childGroup.email());
        verifyMemberInResponse(members, "MEMBER", MEMBER_EMAIL.toLowerCase());
        verifyMemberInResponse(members, "OWNER", OWNER_EMAIL.toLowerCase());
        verifyMemberInResponse(members, "OWNER", getCallerEmail());
    }

    private String getCallerEmail() {
        return entitlementsClient.listGroups().body().principal()
            .orElseThrow(() -> new IllegalStateException("Could not resolve caller identity"))
            .toLowerCase();
    }

    private void verifyMemberInResponse(GroupMembersResponse response, String role, String memberEmail) {
        boolean present = false;
        for (GroupMember member : response.members()) {
            if (memberEmail.equalsIgnoreCase(member.email()) && role.equals(member.role())) {
                present = true;
                break;
            }
        }
        assertTrue(present, "Expected member " + memberEmail + " with role " + role);
    }

    private void verifyConflictError(String groupEmail, String memberEmail, String role) {
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.addMemberToGroup(groupEmail, new AddMemberRequest(memberEmail, role)));
        assertEquals(HttpStatus.SC_CONFLICT, exception.getStatusCode());
        String expectedMessage =
            String.format("%s is already a member of group %s", memberEmail.toLowerCase(), groupEmail);
        // os-core-test 0.1.6 stores the raw error body in AppError.message, so assert containment.
        assertTrue(exception.getError().getMessage().contains(expectedMessage));
    }
}

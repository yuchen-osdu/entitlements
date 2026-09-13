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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
        Group group = entitlementsClient.createGroup("group-" + currentTime, "desc").body();
        Group child = entitlementsClient.createGroup("child-group-name" + currentTime, "desc").body();
        entitlementsClient.addMemberToGroup(group.email(), child.email(), "MEMBER");

        entitlementsClient.removeMemberFromGroup(group.email(), child.email());

        GroupMember[] members = entitlementsClient.listGroupMembers(group.email()).body().members();
        assertFalse(Arrays.stream(members).anyMatch(m -> m.email().equals(child.email())));
        // child group still exists
        assertEquals(HttpStatus.SC_OK, entitlementsClient.listGroupMembers(child.email()).statusCode());
    }

    /**
     * A user that belongs to other groups cannot be removed from the elementary data-partition
     * users group; removal must be done via the delete-member endpoint instead.
     */
    @Test
    void shouldFailToRemoveMemberFromElementaryDPGroupIfUserIsMemberOfOtherGroups() {
        String userName = memberToBeDeleted(currentTime);
        String elementaryUsersGroup = groupEmail("users");

        Group group = entitlementsClient.createGroup("group-" + currentTime, "desc").body();
        entitlementsClient.addMemberToGroup(group.email(), userName, "MEMBER");
        entitlementsClient.addMemberToGroup(elementaryUsersGroup, userName, "MEMBER");

        // removal from the elementary data-partition users group is rejected with 400
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.removeMemberFromGroup(elementaryUsersGroup, userName));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());

        // delete-member removes the user from every group
        entitlementsClient.deleteMember(userName);

        GroupsResponse groups =
            entitlementsClient.listMemberGroups(userName, Map.of("type", "NONE")).body();
        GroupReference[] memberGroups = groups.groups();
        assertTrue(memberGroups == null || memberGroups.length == 0);
    }

    /**
     * Delete a group-member removes it from every parent group, but leaves the child group itself
     * intact.
     */
    @Test
    void shouldSuccessfullyDeleteGroupMember() throws Exception {
        List<Group> g = setupGroupsWithSharedMember();
        assertTrue(isMemberOf(g.get(2).email(), g.get(0).email()));
        assertTrue(isMemberOf(g.get(2).email(), g.get(1).email()));

        entitlementsClient.deleteMember(g.get(2).email());
        Thread.sleep(1500);

        assertFalse(isMemberOf(g.get(2).email(), g.get(0).email()));
        assertFalse(isMemberOf(g.get(2).email(), g.get(1).email()));
    }

    /**
     * Delete a user-member removes it from every group in the partition.
     */
    @Test
    void shouldSuccessfullyDeleteUserMember() {
        String member = memberToBeDeleted(currentTime);
        List<Group> g = setupGroupsWithMember(member);
        entitlementsClient.deleteMember(member);
        assertFalse(isMemberOf(member, g.get(0).email()));
        assertFalse(isMemberOf(member, g.get(1).email()));
    }

    /**
     * Concurrent delete-member calls must all succeed: idempotent 2xx or 404 when another
     * thread already removed the member.
     */
    @Test
    void shouldBeAbleToInvokeDeleteMemberApiInParallel() throws Exception {
        String member = memberToBeDeleted(currentTime);
        setupGroupsWithMember(member);
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> deleteMemberSucceeded(member));
        }
        List<Future<Boolean>> responses = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        long successes = 0;
        for (Future<Boolean> future : responses) {
            if (Boolean.TRUE.equals(future.get())) {
                successes++;
            }
        }
        assertEquals(threads, successes, "Expected 10 successful responses");
    }

    /** A delete is "successful" when it returns 2xx, or 404 because another thread already removed it. */
    private boolean deleteMemberSucceeded(String memberEmail) {
        try {
            entitlementsClient.deleteMember(memberEmail);
            return true;
        } catch (ClientException exception) {
            return exception.getStatusCode() == HttpStatus.SC_NOT_FOUND;
        }
    }

    private List<Group> setupGroupsWithSharedMember() {
        List<Group> g = new ArrayList<>();
        g.add(entitlementsClient.createGroup("group1-" + currentTime, "desc").body());
        g.add(entitlementsClient.createGroup("group2-" + currentTime, "desc").body());
        g.add(entitlementsClient.createGroup("group3-" + currentTime, "desc").body());
        entitlementsClient.addMemberToGroup(g.get(0).email(), g.get(2).email(), "MEMBER");
        entitlementsClient.addMemberToGroup(g.get(1).email(), g.get(2).email(), "MEMBER");
        return g;
    }

    private List<Group> setupGroupsWithMember(String member) {
        List<Group> g = new ArrayList<>();
        g.add(entitlementsClient.createGroup("group1-" + currentTime, "desc").body());
        g.add(entitlementsClient.createGroup("group2-" + currentTime, "desc").body());
        entitlementsClient.addMemberToGroup(g.get(0).email(), member, "MEMBER");
        entitlementsClient.addMemberToGroup(g.get(1).email(), member, "MEMBER");
        return g;
    }

    private boolean isMemberOf(String memberEmail, String groupEmail) {
        return Arrays.stream(entitlementsClient.listGroupMembers(groupEmail).body().members())
            .anyMatch(m -> m.email().equals(memberEmail));
    }
}

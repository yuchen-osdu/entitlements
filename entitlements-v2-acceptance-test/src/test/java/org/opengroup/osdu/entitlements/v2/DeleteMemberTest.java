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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.model.entitlements.Group;

@Disabled
public class DeleteMemberTest extends BaseEntitlementsAcceptanceTest {

    private final long currentTime = System.currentTimeMillis();

    @Test
    void shouldSuccessfullyDeleteGroupMember() throws Exception {
        List<Group> g = setupGroups();
        assertTrue(isMemberOf(g.get(2).email(), g.get(0).email()));
        assertTrue(isMemberOf(g.get(2).email(), g.get(1).email()));

        entitlementsClient.deleteMember(g.get(2).email(), DEFAULT_USER);
        Thread.sleep(1500);

        assertFalse(isMemberOf(g.get(2).email(), g.get(0).email()));
        assertFalse(isMemberOf(g.get(2).email(), g.get(1).email()));
    }

    @Test
    void shouldSuccessfullyDeleteUserMember() {
        String member = memberToBeDeleted(currentTime);
        List<Group> g = setupUsers(member);
        entitlementsClient.deleteMember(member, DEFAULT_USER);
        assertFalse(isMemberOf(member, g.get(0).email()));
        assertFalse(isMemberOf(member, g.get(1).email()));
    }

    @Test
    void shouldBeAbleToInvokeApiInParallel() throws Exception {
        List<Group> g = setupGroups();
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> deleteMemberSucceeded(g.get(2).email()));
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
            entitlementsClient.deleteMember(memberEmail, DEFAULT_USER);
            return true;
        } catch (ClientException exception) {
            return exception.getStatusCode() == HttpStatus.SC_NOT_FOUND;
        }
    }

    private List<Group> setupGroups() {
        List<Group> g = new ArrayList<>();
        g.add(entitlementsClient.createGroup("group1-" + currentTime, "desc", DEFAULT_USER).body());
        g.add(entitlementsClient.createGroup("group2-" + currentTime, "desc", DEFAULT_USER).body());
        g.add(entitlementsClient.createGroup("group3-" + currentTime, "desc", DEFAULT_USER).body());
        entitlementsClient.addMemberToGroup(g.get(0).email(), g.get(2).email(), "MEMBER", DEFAULT_USER);
        entitlementsClient.addMemberToGroup(g.get(1).email(), g.get(2).email(), "MEMBER", DEFAULT_USER);
        return g;
    }

    private List<Group> setupUsers(String member) {
        List<Group> g = new ArrayList<>();
        g.add(entitlementsClient.createGroup("group1-" + currentTime, "desc", DEFAULT_USER).body());
        g.add(entitlementsClient.createGroup("group2-" + currentTime, "desc", DEFAULT_USER).body());
        entitlementsClient.addMemberToGroup(g.get(0).email(), member, "MEMBER", DEFAULT_USER);
        entitlementsClient.addMemberToGroup(g.get(1).email(), member, "MEMBER", DEFAULT_USER);
        return g;
    }

    private boolean isMemberOf(String memberEmail, String groupEmail) {
        return Arrays.stream(entitlementsClient.listGroupMembers(groupEmail, DEFAULT_USER).body().members())
            .anyMatch(m -> m.email().equals(memberEmail));
    }
}

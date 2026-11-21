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

public class DeleteGroupTest extends BaseEntitlementsAcceptanceTest {

    private final long currentTime = System.currentTimeMillis();

    /**
     * 1) create a group
     * 2) delete a group
     * 3) check group does not exist by verifying 404 when listing its members
     */
    @Test
    void shouldReturn204WhenMakingValidHttpRequest() {
        Group group = entitlementsClient.createGroup("groupName-" + currentTime, "desc").body();

        assertEquals(HttpStatus.SC_NO_CONTENT,
            entitlementsClient.deleteGroup(group.email()).statusCode());

        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.listGroupMembers(group.email()));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestWhenMakingHttpRequestWithInvalidUrl() {
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.deleteGroup("%25"));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());
    }
}

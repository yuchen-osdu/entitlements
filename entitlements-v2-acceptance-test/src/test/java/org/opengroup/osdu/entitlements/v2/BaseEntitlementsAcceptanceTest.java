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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.auth.UserType;
import org.opengroup.osdu.core.test.base.BaseAcceptanceTests;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.EntitlementsClient;
import org.opengroup.osdu.core.test.client.PartitionClient;
import org.opengroup.osdu.core.test.config.EnvLoader;
import org.opengroup.osdu.core.test.service.ServiceType;

/**
 * Base class for Entitlements v2 acceptance tests.
 *
 * <p>Provides a privileged {@link EntitlementsClient} for setup and assertions, a
 * {@link #noAccessEntitlementsClient} for authorization-negative scenarios, and a
 * {@link PartitionClient} for partition metadata lookups.
 */
public abstract class BaseEntitlementsAcceptanceTest extends BaseAcceptanceTests {

    protected static final String MEMBER_EMAIL = "testMember@test.com";
    protected static final String OWNER_EMAIL = "testmMemberOwner@test.com";

    protected final EntitlementsClient entitlementsClient;
    protected final EntitlementsClient noAccessEntitlementsClient;
    protected final PartitionClient partitionClient;

    protected BaseEntitlementsAcceptanceTest() {
        super(List.of(UserType.PRIVILEGED_USER, UserType.NO_ACCESS_USER),
            List.of(ServiceType.ENTITLEMENTS_V2, ServiceType.PARTITION_V1));
        this.entitlementsClient =
            new EntitlementsClient(this.stringHttpClient, UserType.PRIVILEGED_USER);
        this.noAccessEntitlementsClient =
            new EntitlementsClient(this.stringHttpClient, UserType.NO_ACCESS_USER);
        this.partitionClient = new PartitionClient(this.stringHttpClient, UserType.PRIVILEGED_USER);
    }

    @Override
    protected void setup() throws Exception {
        // Shared infrastructure is initialized in the BaseAcceptanceTests constructor.
    }

    @Override
    @AfterEach
    protected void teardown() {
        this.entitlementsClient.teardown();
        this.noAccessEntitlementsClient.teardown();
    }

    @Test
    void shouldReturn401WhenMakingHttpRequestWithoutToken() {
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.listGroups(Map.of(), Map.of(HttpHeaders.AUTHORIZATION, "")));
        int statusCode = exception.getStatusCode();
        assertTrue(statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN,
            "Expected 401 or 403 for a request without valid credentials but was " + statusCode);
    }

    protected String partitionId() {
        return servicesConfig.getDataPartitionId();
    }

    protected String entitlementsDomain() {
        String domain = EnvLoader.get("ENTITLEMENTS_DOMAIN");
        return (domain == null || domain.isBlank()) ? "group" : domain;
    }

    protected String groupEmail(String groupName) {
        return groupName.toLowerCase() + "@" + partitionId() + "." + entitlementsDomain();
    }

    protected String memberToBeDeleted(long timestamp) {
        return String.format("testMember-%s@test.com", timestamp);
    }
}

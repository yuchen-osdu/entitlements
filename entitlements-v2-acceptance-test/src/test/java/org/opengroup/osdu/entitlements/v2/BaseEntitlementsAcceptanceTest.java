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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.auth.UserType;
import org.opengroup.osdu.core.test.base.BaseAcceptanceTests;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.EntitlementsClient;
import org.opengroup.osdu.core.test.config.EnvLoader;
import org.opengroup.osdu.core.test.service.ServiceType;

/**
 * Base class for Entitlements v2 acceptance tests.
 *
 * <p>Replaces the legacy {@code AcceptanceBaseTest} / {@code HttpClientService} /
 * {@code EntitlementsV2Service} / {@code TokenTestUtils} stack with the shared os-core-test
 * infrastructure. It wires a typed {@link EntitlementsClient} backed by the shared
 * {@link #stringHttpClient} and exposes the default {@link UserType} plus the entitlements-specific
 * group-email naming helpers that os-core-test does not provide.
 *
 * <p>Groups created through {@link #entitlementsClient} are tracked and removed in
 * {@link #teardown()}. Subclasses that create extra client instances must call their
 * {@code teardown()} too.
 */
public abstract class BaseEntitlementsAcceptanceTest extends BaseAcceptanceTests {

    /** Privileged user used by the happy-path tests. */
    protected static final UserType DEFAULT_USER = UserType.PRIVILEGED_USER;

    // Domain-only test data (no os-core-test equivalent).
    protected static final String MEMBER_EMAIL = "testMember@test.com";
    protected static final String OWNER_EMAIL = "testmMemberOwner@test.com";

    /** Typed client for the Entitlements v2 API. */
    protected EntitlementsClient entitlementsClient;

    protected BaseEntitlementsAcceptanceTest() {
        super(List.of(UserType.PRIVILEGED_USER), List.of(ServiceType.ENTITLEMENTS_V2));
    }

    @BeforeEach
    @Override
    protected void setup() throws Exception {
        this.entitlementsClient = new EntitlementsClient(stringHttpClient);
    }

    @AfterEach
    @Override
    protected void teardown() throws Exception {
        if (entitlementsClient != null) {
            entitlementsClient.teardown();
        }
    }

    /**
     * Shared contract test: a request without valid credentials must be rejected.
     *
     * <p>os-core-test always authenticates with a {@link UserType}; to exercise the unauthenticated
     * path the {@code Authorization} header is overridden with an empty value via the client's
     * custom-header support. Because the header is present-but-invalid (rather than absent), the
     * gateway answers with {@code 401 Unauthorized} or {@code 403 Forbidden}; both are accepted as
     * "rejected without valid credentials".
     */
    @Test
    void shouldReturn401WhenMakingHttpRequestWithoutToken() {
        ClientException exception = assertThrows(ClientException.class,
            () -> entitlementsClient.listGroups(getDefaultUser(), Map.of(),
                Map.of(HttpHeaders.AUTHORIZATION, "")));
        int statusCode = exception.getStatusCode();
        assertTrue(statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN,
            "Expected 401 or 403 for a request without valid credentials but was " + statusCode);
    }

    // ---------------------------------------------------------------------
    // Entitlements group-email naming helpers (no os-core-test equivalent)
    // ---------------------------------------------------------------------

    /** Data partition id from the shared os-core-test configuration. */
    protected String partitionId() {
        return servicesConfig.getDataPartitionId();
    }

    /** Entitlements group domain ({@code ENTITLEMENTS_DOMAIN}, default {@code group}). */
    protected String entitlementsDomain() {
        String domain = EnvLoader.get("ENTITLEMENTS_DOMAIN");
        return (domain == null || domain.isBlank()) ? "group" : domain;
    }

    /** Builds the well-known group email for a group name: {@code name@partition.domain}. */
    protected String groupEmail(String groupName) {
        return groupName.toLowerCase() + "@" + partitionId() + "." + entitlementsDomain();
    }

    /** A unique member email used by delete-member scenarios. */
    protected String memberToBeDeleted(long timestamp) {
        return String.format("testMember-%s@test.com", timestamp);
    }
}

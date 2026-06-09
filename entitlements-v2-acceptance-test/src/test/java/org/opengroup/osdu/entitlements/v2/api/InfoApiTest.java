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

package org.opengroup.osdu.entitlements.v2.api;

import static org.opengroup.osdu.core.test.base.GetInfoAssertions.assertInfoResponse;

import java.util.List;
import org.apache.hc.core5.http.Method;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.auth.UserType;
import org.opengroup.osdu.core.test.base.BaseGetInfoAcceptanceTests;
import org.opengroup.osdu.core.test.service.ServiceType;

/**
 * Validates the Entitlements {@code /info} endpoint via the shared os-core-test base.
 *
 * <p>The inherited {@code should_returnInfo} test covers the canonical {@code /info} path.
 * Entitlements exposes no feature flags through {@code /info}, so the expected feature-flag list is
 * empty (it is only consulted when {@code EXPOSE_FEATUREFLAG_ENABLED} is set).
 */
public class InfoApiTest extends BaseGetInfoAcceptanceTests {

    private static final List<String> EXPECTED_FEATURE_FLAGS = List.of();

    public InfoApiTest() {
        super(UserType.PRIVILEGED_USER, ServiceType.ENTITLEMENTS_V2, EXPECTED_FEATURE_FLAGS);
    }
}

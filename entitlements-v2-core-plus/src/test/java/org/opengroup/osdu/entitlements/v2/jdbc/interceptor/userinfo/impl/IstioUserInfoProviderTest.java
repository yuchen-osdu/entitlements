/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.entitlements.v2.jdbc.interceptor.userinfo.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;

@ExtendWith(MockitoExtension.class)
class IstioUserInfoProviderTest {

    @Mock
    private JaxRsDpsLog log;

    @InjectMocks
    private IstioUserInfoProvider sut;

    private static String buildPlainToken(String subject, String email) {
        // A "plain" (unsigned, alg=none) JWT is sufficient — Istio user-info parsing is claims-based
        // and does not verify the signature at this layer.
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("email", email)
                .build();
        return new PlainJWT(claims).serialize();
    }

    @Test
    void getUserInfoFromToken_withBearerPrefix_stripsPrefixAndReturnsClaimsAsUserInfo() {
        String token = "Bearer " + buildPlainToken("subject-1", "user@example.com");

        UserInfo info = sut.getUserInfoFromToken(token);

        assertNotNull(info);
        assertEquals("subject-1", info.getSubject().getValue());
        // Extracted claim propagated verbatim into the UserInfo view.
        assertEquals("user@example.com", info.getEmailAddress());
    }

    @Test
    void getUserInfoFromToken_withoutBearerPrefix_parsesTokenAsIs() {
        String token = buildPlainToken("subject-2", "someone@example.com");

        UserInfo info = sut.getUserInfoFromToken(token);

        assertNotNull(info);
        assertEquals("subject-2", info.getSubject().getValue());
    }

    @Test
    void getUserInfoFromToken_malformedToken_throwsAppExceptionUnauthorized() {
        AppException ex = assertThrows(AppException.class,
                () -> sut.getUserInfoFromToken("not-a-jwt"));

        // Contract: any parse failure at this layer maps to 401 Unauthorized.
        assertEquals(401, ex.getError().getCode());
    }

    @Test
    void getUserInfoFromToken_bearerPrefixWithMalformedBody_throwsAppExceptionUnauthorized() {
        AppException ex = assertThrows(AppException.class,
                () -> sut.getUserInfoFromToken("Bearer not-a-jwt"));
        assertEquals(401, ex.getError().getCode());
    }
}

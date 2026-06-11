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

package org.opengroup.osdu.entitlements.v2.jdbc.interceptor.authenticator.impl;

import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.entitlements.v2.jdbc.config.properties.EntConfigProperties;
import org.opengroup.osdu.entitlements.v2.jdbc.config.properties.OpenIdProviderProperties;
import org.opengroup.osdu.entitlements.v2.jdbc.interceptor.userinfo.IUserInfoProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAuthenticatorTest {

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private EntConfigProperties properties;

    @Mock
    private IUserInfoProvider userInfoProvider;

    @Mock
    private OpenIdProviderProperties openIdProperties;

    @Mock
    private HttpServletRequest request;

    @Mock
    private UserInfo mockUserInfo;

    private InternalAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new InternalAuthenticator(dpsHeaders, properties, userInfoProvider, openIdProperties);
        when(properties.getUserIdentityHeaderName()).thenReturn("x-user-id");
    }

    @Test
    void shouldAuthenticateWithConfiguredClaim() {
        // Given
        String token = "Bearer test-token";
        String expectedUserId = "user@example.com";
        
        JSONObject claims = new JSONObject();
        claims.put("sub", "test-sub");
        claims.put("email", expectedUserId);
        UserInfo userInfo = new UserInfo(claims);
        
        when(request.getHeader(DpsHeaders.AUTHORIZATION)).thenReturn(token);
        when(request.getHeader("x-user-id")).thenReturn(null);
        when(userInfoProvider.getUserInfoFromToken(token)).thenReturn(userInfo);
        when(openIdProperties.getUserIdClaimName()).thenReturn("email");

        // When
        boolean result = authenticator.requestIsAuthenticated(request);

        // Then
        assertTrue(result);
        verify(dpsHeaders).put(DpsHeaders.USER_ID, expectedUserId);
    }

    @Test
    void shouldFallbackToSubClaimWhenConfiguredClaimIsNull() {
        // Given
        String token = "Bearer test-token";
        String expectedUserId = "test-sub-value";
        
        UserInfo userInfo = new UserInfo(new Subject(expectedUserId));
        
        when(request.getHeader(DpsHeaders.AUTHORIZATION)).thenReturn(token);
        when(request.getHeader("x-user-id")).thenReturn(null);
        when(userInfoProvider.getUserInfoFromToken(token)).thenReturn(userInfo);
        when(openIdProperties.getUserIdClaimName()).thenReturn("email");

        // When
        boolean result = authenticator.requestIsAuthenticated(request);

        // Then
        assertTrue(result);
        verify(dpsHeaders).put(DpsHeaders.USER_ID, expectedUserId);
    }

    @Test
    void shouldReturnFalseWhenBothConfiguredClaimAndSubAreNull() {
        // Given
        String token = "Bearer test-token";
        
        when(request.getHeader(DpsHeaders.AUTHORIZATION)).thenReturn(token);
        when(request.getHeader("x-user-id")).thenReturn(null);
        when(userInfoProvider.getUserInfoFromToken(token)).thenReturn(mockUserInfo);
        when(openIdProperties.getUserIdClaimName()).thenReturn("email");
        when(mockUserInfo.getStringClaim("email")).thenReturn(null);
        when(mockUserInfo.getSubject()).thenReturn(null);

        // When
        boolean result = authenticator.requestIsAuthenticated(request);

        // Then
        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenNoAuthorizationHeader() {
        // Given
        when(request.getHeader(DpsHeaders.AUTHORIZATION)).thenReturn(null);

        // When
        boolean result = authenticator.requestIsAuthenticated(request);

        // Then
        assertFalse(result);
    }
}

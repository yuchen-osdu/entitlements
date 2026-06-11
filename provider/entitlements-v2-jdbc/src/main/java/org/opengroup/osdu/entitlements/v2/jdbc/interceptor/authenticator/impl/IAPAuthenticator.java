/*
 *  Copyright 2020-2024 Google LLC
 *  Copyright 2020-2024 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.entitlements.v2.jdbc.interceptor.authenticator.impl;

import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.entitlements.v2.jdbc.config.properties.IapConfigurationProperties;
import org.opengroup.osdu.entitlements.v2.jdbc.config.properties.OpenIdProviderProperties;
import org.opengroup.osdu.entitlements.v2.jdbc.interceptor.authenticator.IAuthenticator;
import org.opengroup.osdu.entitlements.v2.jdbc.interceptor.userinfo.IUserInfoProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(value = "authentication-mode", havingValue = "IAP")
@RequiredArgsConstructor
public class IAPAuthenticator implements IAuthenticator {

    private final DpsHeaders dpsHeaders;
    private final IapConfigurationProperties iapConfigurationProperties;
    private final IUserInfoProvider userInfoProvider;
    private final OpenIdProviderProperties openIdProperties;

    @Override
    public boolean requestIsAuthenticated(HttpServletRequest request) {
        Optional<String> authorization = Optional.ofNullable(request.getHeader(DpsHeaders.AUTHORIZATION));
        Optional<String> iapToken = Optional.ofNullable(request.getHeader(iapConfigurationProperties.getJwtHeader()));
        Optional<String> userIdentity = Optional.ofNullable(request.getHeader(iapConfigurationProperties.getUserIdHeader()));

        if (!iapToken.isPresent() && !authorization.isPresent()) {
            log.warn("Request unauthenticated, iap token or authorization are required.");
            return false;
        }

        if (iapToken.isPresent() && userIdentity.isPresent()) {
            log.debug("Both, IAP token and header {} are present, verify that they are related to the same email.",
                iapConfigurationProperties.getUserIdHeader());
            String userId = userInfoProvider.getUserInfoFromToken(iapToken.get())
                .getStringClaim(openIdProperties.getUserIdClaimName());
            String[] userIdHeaderKeyValue = userIdentity.get().split(":");
            if (userIdHeaderKeyValue.length < 2) {
                log.warn("Request unauthenticated, malformed user email header, should be in format <account>:<email>.");
                return false;
            }
            String emailFromHeader = userIdHeaderKeyValue[1];
            dpsHeaders.put(DpsHeaders.USER_ID, userId);
            return userId.equals(emailFromHeader);
        }

        if (authorization.isPresent()) {
            log.debug("IAP token and header {} are missing, authorization token present, validation through OpenID provider.",
                iapConfigurationProperties.getUserIdHeader());
            String userId = userInfoProvider.getUserInfoFromToken(authorization.get())
                .getStringClaim(openIdProperties.getUserIdClaimName());
            dpsHeaders.put(DpsHeaders.USER_ID, userId);
            return true;
        }
        return false;
    }
}

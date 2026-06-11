//  Copyright © Microsoft Corporation
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package org.opengroup.osdu.entitlements.v2.auth;

import org.apache.commons.lang3.StringUtils;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.http.RequestInfo;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.entitlements.v2.util.RequestInfoUtilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationFilter {
    @Autowired
    private AuthorizationService authService;
    @Autowired
    private RequestInfo requestInfo;
    @Autowired
    private JaxRsDpsLog log;
    @Autowired
    private RequestInfoUtilService requestInfoUtilService;

    public boolean hasAnyPermission(String... requiredRoles) {
        log.debug(String.format("authorizeAny timestamp: %d", System.currentTimeMillis()));
        DpsHeaders headers = requestInfo.getHeaders();
        if (StringUtils.isBlank(headers.getAuthorization())) {
            throw AppException.createUnauthorized("No credentials sent on request.");
        }
        String user = requestInfoUtilService.getUserId(headers);
        if (user == null ){
            throw AppException.createUnauthorized("No User Id header provided");
        }
        requestInfo.getHeaders().put(DpsHeaders.USER_EMAIL, user);
        TenantInfo tenantInfo = requestInfo.getTenantInfo();
        if (tenantInfo == null) {
            throw new AppException(HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Invalid data partition id");
        }
        if (user.equalsIgnoreCase(tenantInfo.getServiceAccount())) {
            // Service account is authorized, populate authorized group
            headers.put(DpsHeaders.USER_AUTHORIZED_GROUP_NAME, "service.admin");
            return true;
        }
        if (requiredRoles.length <= 0) {
            throw AppException.createUnauthorized("The user is not authorized to perform this action");
        }
        boolean authorized = authService.isCurrentUserAuthorized(headers, requiredRoles);
        if (authorized) {
            // Populate the authorized group name in headers for audit logging
            String authorizedGroup = authService.getAuthorizedGroupName(headers, requiredRoles);
            if (authorizedGroup != null) {
                headers.put(DpsHeaders.USER_AUTHORIZED_GROUP_NAME, authorizedGroup);
            }
        }
        return authorized;
    }

    public boolean requesterHasImpersonationPermission(String role) {
        DpsHeaders headers = requestInfo.getHeaders();
        String userId = requestInfoUtilService.getUserId(headers);
        requestInfo.getTenantInfo();
        if (!authService.isGivenUserAuthorized(userId, headers.getPartitionId(), role)) {
            log.error("Delegation group not found");
            throw new AppException(HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Impersonation not allowed for " + userId);
        } else {
            return true;
        }
    }

    public boolean targetCanBeImpersonated(String role) {
        DpsHeaders headers = requestInfo.getHeaders();
        TenantInfo tenantInfo = requestInfo.getTenantInfo();
        String impersonationTarget = requestInfoUtilService.getImpersonationTarget(headers);
        if (impersonationTarget.equalsIgnoreCase(tenantInfo.getServiceAccount())) {
            log.error("Impersonation attempt of a tenant service account.");
            throw AppException.createForbidden(
                "Tenant service account impersonation is not allowed.");
        }
        if (!authService.isGivenUserAuthorized(impersonationTarget, headers.getPartitionId(), role)) {
            log.error("Impersonation group not found");
            throw new AppException(HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Impersonation not allowed for " + impersonationTarget);
        } else {
            return true;
        }
    }
}
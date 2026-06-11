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

package org.opengroup.osdu.entitlements.v2.service;

import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.RequestInfo;
import org.opengroup.osdu.entitlements.v2.AppProperties;
import org.opengroup.osdu.entitlements.v2.auth.AuthorizationService;
import org.opengroup.osdu.entitlements.v2.logging.AuditLogger;
import org.opengroup.osdu.entitlements.v2.model.listmember.ListMemberServiceDto;
import org.opengroup.osdu.entitlements.v2.model.ChildrenReference;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.spi.listmember.ListMemberRepo;
import org.opengroup.osdu.entitlements.v2.spi.retrievegroup.RetrieveGroupRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListMemberService {

    private final RetrieveGroupRepo retrieveGroupRepo;
    private final ListMemberRepo listMemberRepo;
    private final JaxRsDpsLog log;
    private final PermissionService permissionService;
    private final AuthorizationService authorizationService;
    private final RequestInfo requestInfo;
    private final AuditLogger auditLogger;

    private static final String NOT_AUTHORIZED_MESSAGE = "Not authorized to manage members";

    public List<ChildrenReference> run(ListMemberServiceDto listMemberServiceDto) {
        log.debug(String.format("requested by %s", listMemberServiceDto.getRequesterId()));
        EntityNode groupNode = retrieveGroupRepo.groupExistenceValidation(listMemberServiceDto.getGroupId(), listMemberServiceDto.getPartitionId());
        EntityNode requesterNode = EntityNode.createMemberNodeForRequester(listMemberServiceDto.getRequesterId(), listMemberServiceDto.getPartitionId());
        if (!isCallerHasAdminPermissions() && !permissionService.hasOwnerPermissionOf(requesterNode, groupNode)) {
            throw new AppException(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(), NOT_AUTHORIZED_MESSAGE);
        }
        try {
            List<ChildrenReference> result = listMemberRepo.run(listMemberServiceDto);
            auditLogger.listMemberSuccess(listMemberServiceDto.getGroupId());
            return result;
        } catch (Exception e) {
            auditLogger.listMemberFailure(listMemberServiceDto.getGroupId());
            throw e;
        }
    }

    private boolean isCallerHasAdminPermissions() {
        try {
            return authorizationService.isCurrentUserAuthorized(requestInfo.getHeaders(), AppProperties.ADMIN);
        } catch (AppException e) {
            log.warning("Caller does not have ADMIN permissions", e);
            return false;
        }
    }
}

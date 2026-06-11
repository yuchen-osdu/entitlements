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

package org.opengroup.osdu.entitlements.v2.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.http.RequestInfo;
import org.opengroup.osdu.core.common.util.IpAddressUtil;
import org.opengroup.osdu.entitlements.v2.model.Role;
import org.opengroup.osdu.entitlements.v2.util.RequestInfoUtilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.List;
import java.util.Set;

/**
 * Request-scoped audit logger for entitlements operations.
 *
 * This logger captures audit context once per request and provides methods
 * for logging various entitlements operations with proper audit trail information.
 */
@Component
@RequestScope
public class AuditLogger {
    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    private RequestInfo requestInfo;

    @Autowired
    private RequestInfoUtilService requestInfoUtilService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    private AuditEvents events = null;

    private AuditEvents getEvents() {
        if (this.events == null) {
            DpsHeaders headers = this.requestInfo.getHeaders();
            String userId = requestInfoUtilService.getUserId(headers);
            String userIpAddress = IpAddressUtil.getClientIpAddress(httpServletRequest);
            String userAgent = httpServletRequest.getHeader("User-Agent");
            String userAuthorizedGroupName = headers.getUserAuthorizedGroupName();
            this.events = new AuditEvents(userId, headers.getPartitionId(), userIpAddress, userAgent, userAuthorizedGroupName);
        }
        return this.events;
    }

    // ==================== Group Operations ====================

    public void createGroupSuccess(String groupId) {
        this.writeLog(this.getEvents().getCreateGroupSuccessEvent(groupId, AuditOperation.CREATE_GROUP.getRequiredGroups()));
    }

    public void createGroupFailure(String groupId) {
        this.writeLog(this.getEvents().getCreateGroupFailureEvent(groupId, AuditOperation.CREATE_GROUP.getRequiredGroups()));
    }

    public void updateGroupSuccess(String groupId) {
        this.writeLog(this.getEvents().getUpdateGroupSuccessEvent(groupId, AuditOperation.UPDATE_GROUP.getRequiredGroups()));
    }

    public void updateGroupFailure(String groupId) {
        this.writeLog(this.getEvents().getUpdateGroupFailureEvent(groupId, AuditOperation.UPDATE_GROUP.getRequiredGroups()));
    }

    public void deleteGroupSuccess(String groupId) {
        this.writeLog(this.getEvents().getDeleteGroupSuccessEvent(groupId, AuditOperation.DELETE_GROUP.getRequiredGroups()));
    }

    public void deleteGroupFailure(String groupId) {
        this.writeLog(this.getEvents().getDeleteGroupFailureEvent(groupId, AuditOperation.DELETE_GROUP.getRequiredGroups()));
    }

    // ==================== Member Operations ====================

    public void addMemberSuccess(String groupId, String memberId, Role role) {
        this.writeLog(this.getEvents().getAddMemberSuccessEvent(groupId, memberId, role, AuditOperation.ADD_MEMBER.getRequiredGroups()));
    }

    public void addMemberFailure(String groupId, String memberId, Role role) {
        this.writeLog(this.getEvents().getAddMemberFailureEvent(groupId, memberId, role, AuditOperation.ADD_MEMBER.getRequiredGroups()));
    }

    /**
     * Log successful member removal with default REMOVE_MEMBER operation.
     */
    public void removeMemberSuccess(String groupId, String memberId, String requesterId) {
        removeMemberSuccess(groupId, memberId, requesterId, AuditOperation.REMOVE_MEMBER);
    }

    /**
     * Log failed member removal with default REMOVE_MEMBER operation.
     */
    public void removeMemberFailure(String groupId, String memberId, String requesterId) {
        removeMemberFailure(groupId, memberId, requesterId, AuditOperation.REMOVE_MEMBER);
    }

    /**
     * Log successful member removal with specified operation.
     * Use DELETE_MEMBER when called from DeleteMemberService for correct required groups.
     */
    public void removeMemberSuccess(String groupId, String memberId, String requesterId, AuditOperation operation) {
        this.writeLog(this.getEvents().getRemoveMemberSuccessEvent(groupId, memberId, requesterId, operation.getRequiredGroups()));
    }

    /**
     * Log failed member removal with specified operation.
     * Use DELETE_MEMBER when called from DeleteMemberService for correct required groups.
     */
    public void removeMemberFailure(String groupId, String memberId, String requesterId, AuditOperation operation) {
        this.writeLog(this.getEvents().getRemoveMemberFailureEvent(groupId, memberId, requesterId, operation.getRequiredGroups()));
    }

    // ==================== Read Operations ====================

    public void listGroupSuccess(List<String> groupIds) {
        this.writeLog(this.getEvents().getListGroupSuccessEvent(groupIds, AuditOperation.LIST_GROUP.getRequiredGroups()));
    }

    public void listGroupFailure(List<String> groupIds) {
        this.writeLog(this.getEvents().getListGroupFailureEvent(groupIds, AuditOperation.LIST_GROUP.getRequiredGroups()));
    }

    public void listMemberSuccess(String groupId) {
        this.writeLog(this.getEvents().getListMemberSuccessEvent(groupId, AuditOperation.LIST_MEMBER.getRequiredGroups()));
    }

    public void listMemberFailure(String groupId) {
        this.writeLog(this.getEvents().getListMemberFailureEvent(groupId, AuditOperation.LIST_MEMBER.getRequiredGroups()));
    }

    // ==================== App ID Operations ====================

    public void updateAppIdsSuccess(String groupId, Set<String> appIds) {
        this.writeLog(this.getEvents().getUpdateAppIdsSuccessEvent(groupId, appIds, AuditOperation.UPDATE_APP_IDS.getRequiredGroups()));
    }

    public void updateAppIdsFailure(String groupId, Set<String> appIds) {
        this.writeLog(this.getEvents().getUpdateAppIdsFailureEvent(groupId, appIds, AuditOperation.UPDATE_APP_IDS.getRequiredGroups()));
    }

    // ==================== Cache Operations ====================

    public void updateGroupsInCacheForKeysSuccess(Set<String> keys) {
        this.writeLog(this.getEvents().getUpdateGroupsInCacheForKeysSuccessEvent(keys, AuditOperation.UPDATE_CACHE.getRequiredGroups()));
    }

    public void updateGroupsInCacheForKeysFailure(Set<String> keys) {
        this.writeLog(this.getEvents().getUpdateGroupsInCacheForKeysFailureEvent(keys, AuditOperation.UPDATE_CACHE.getRequiredGroups()));
    }

    // ==================== Redis Backup Operations (IBM provider specific) ====================

    public void redisInstanceBackupSuccess() {
        this.writeLog(this.getEvents().getRedisBackupSuccessEvent(AuditOperation.REDIS_BACKUP.getRequiredGroups()));
    }

    public void redisInstanceBackupFailure() {
        this.writeLog(this.getEvents().getRedisBackupFailureEvent(AuditOperation.REDIS_BACKUP.getRequiredGroups()));
    }

    public void redisInstanceBackupVersionsSuccess() {
        this.writeLog(this.getEvents().getRedisBackupVersionSuccessEvent(AuditOperation.REDIS_BACKUP_VERSIONS.getRequiredGroups()));
    }

    public void redisInstanceBackupVersionsFailure() {
        this.writeLog(this.getEvents().getRedisBackupVersionFailureEvent(AuditOperation.REDIS_BACKUP_VERSIONS.getRequiredGroups()));
    }

    public void redisInstanceRestoreSuccess(String version) {
        this.writeLog(this.getEvents().getRedisRestoreSuccessEvent(version, AuditOperation.REDIS_RESTORE.getRequiredGroups()));
    }

    public void redisInstanceRestoreFailure(String version) {
        this.writeLog(this.getEvents().getRedisRestoreFailureEvent(version, AuditOperation.REDIS_RESTORE.getRequiredGroups()));
    }

    private void writeLog(AuditPayload log) {
        this.logger.audit(log);
    }
}

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

import com.google.common.base.Strings;
import org.opengroup.osdu.entitlements.v2.model.Role;
import org.opengroup.osdu.entitlements.v2.util.JsonConverter;
import org.opengroup.osdu.core.common.logging.audit.AuditAction;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload.AuditPayloadBuilder;
import org.opengroup.osdu.core.common.logging.audit.AuditStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;

class AuditEvents {

    private static final String UNKNOWN = "unknown";
    private static final String UNKNOWN_IP = "0.0.0.0";

    private static final String REDIS_BACKUP_ACTION_ID = "ET201";
    private static final String REDIS_BACKUP_MESSAGE = "Back up redis instance";
    private static final String REDIS_LIST_BACKUP_VERSIONS_ACTION_ID = "ET202";
    private static final String REDIS_LIST_BACKUP_VERSIONS_MESSAGE = "List backup version of the redis instance";
    private static final String REDIS_RESTORE_ACTION_ID = "ET203";
    private static final String REDIS_RESTORE_MESSAGE = "Restore redis instance from version %s";
    private static final String REDIS_CREATE_GROUP_TRANSACTION_ACTION_ID = "ET210";
    private static final String REDIS_CREATE_GROUP_TRANSACTION_MESSAGE = "Create group %s";
    private static final String REDIS_DELETE_GROUP_TRANSACTION_ACTION_ID = "ET211";
    private static final String REDIS_DELETE_GROUP_TRANSACTION_MESSAGE = "Delete group %s";
    private static final String REDIS_LIST_GROUP_TRANSACTION_ACTION_ID = "ET212";
    private static final String REDIS_LIST_GROUP_TRANSACTION_MESSAGE = "Return all groups the caller belongs to of the tenant";
    private static final String REDIS_ADD_MEMBER_TRANSACTION_ACTION_ID = "ET213";
    private static final String REDIS_ADD_MEMBER_TRANSACTION_MESSAGE = "Add entity %s to group %s as %s";
    private static final String REDIS_REMOVE_MEMBER_TRANSACTION_ACTION_ID = "ET214";
    private static final String REDIS_REMOVE_MEMBER_TRANSACTION_MESSAGE = "Remove entity %s to group %s as requested by %s";
    private static final String REDIS_LIST_MEMBER_TRANSACTION_ACTION_ID = "ET215";
    private static final String REDIS_LIST_MEMBER_TRANSACTION_MESSAGE = "Return all direct members of group %s";
    private static final String REDIS_UPDATE_APP_IDS_TRANSACTION_ACTION_ID = "ET216";
    private static final String REDIS_UPDATE_APP_IDS_TRANSACTION_MESSAGE = "Update appId for group %s to %s";
    private static final String REDIS_UPDATE_GROUP_TRANSACTION_ACTION_ID = "ET217";
    private static final String REDIS_UPDATE_GROUP_TRANSACTION_MESSAGE = "Update group %s";
    private static final String REDIS_UPDATE_GROUPS_IN_CACHE_FOR_KEYS_ACTION_ID = "ET219";
    private static final String REDIS_UPDATE_GROUPS_IN_CACHE_FOR_KEYS_MESSAGE = "Update groups in cache for given keys";

    private final String user;
    private final String dataPartitionId;
    private final String userIpAddress;
    private final String userAgent;
    private final String userAuthorizedGroupName;

    AuditEvents(String user, String dataPartitionId, String userIpAddress, String userAgent, String userAuthorizedGroupName) {
        this.user = Strings.isNullOrEmpty(user) ? UNKNOWN : user;
        this.dataPartitionId = dataPartitionId;
        this.userIpAddress = Strings.isNullOrEmpty(userIpAddress) ? UNKNOWN_IP : userIpAddress;
        this.userAgent = Strings.isNullOrEmpty(userAgent) ? UNKNOWN : userAgent;
        this.userAuthorizedGroupName = Strings.isNullOrEmpty(userAuthorizedGroupName) ? UNKNOWN : userAuthorizedGroupName;
    }

    /**
     * Creates an AuditPayload builder pre-populated with common audit fields.
     * Event-specific methods should add action, message, and resources before calling build().
     */
    private AuditPayloadBuilder createAuditPayloadBuilder(
            List<String> requiredGroupsForAction, AuditStatus auditStatus, String actionId) {
        return AuditPayload.builder()
                .status(auditStatus)
                .user(this.user)
                .actionId(actionId)
                .requiredGroupsForAction(requiredGroupsForAction)
                .userIpAddress(this.userIpAddress)
                .userAgent(this.userAgent)
                .userAuthorizedGroupName(this.userAuthorizedGroupName);
    }

    // Redis backup operations - Success/Failure paired methods
    AuditPayload getRedisBackupSuccessEvent(List<String> requiredGroupsForAction) {
        return buildRedisBackupEvent(AuditStatus.SUCCESS, requiredGroupsForAction);
    }

    AuditPayload getRedisBackupFailureEvent(List<String> requiredGroupsForAction) {
        return buildRedisBackupEvent(AuditStatus.FAILURE, requiredGroupsForAction);
    }

    private AuditPayload buildRedisBackupEvent(AuditStatus auditStatus, List<String> requiredGroupsForAction) {
        return createAuditPayloadBuilder(requiredGroupsForAction, auditStatus, REDIS_BACKUP_ACTION_ID)
                .action(AuditAction.JOB_RUN)
                .message(REDIS_BACKUP_MESSAGE)
                .resources(singletonList(dataPartitionId))
                .build();
    }

    AuditPayload getRedisBackupVersionSuccessEvent(List<String> requiredGroupsForAction) {
        return buildRedisBackupVersionEvent(AuditStatus.SUCCESS, requiredGroupsForAction);
    }

    AuditPayload getRedisBackupVersionFailureEvent(List<String> requiredGroupsForAction) {
        return buildRedisBackupVersionEvent(AuditStatus.FAILURE, requiredGroupsForAction);
    }

    private AuditPayload buildRedisBackupVersionEvent(AuditStatus auditStatus, List<String> requiredGroupsForAction) {
        return createAuditPayloadBuilder(requiredGroupsForAction, auditStatus, REDIS_LIST_BACKUP_VERSIONS_ACTION_ID)
                .action(AuditAction.READ)
                .message(REDIS_LIST_BACKUP_VERSIONS_MESSAGE)
                .resources(singletonList(dataPartitionId))
                .build();
    }

    AuditPayload getRedisRestoreSuccessEvent(String version, List<String> requiredGroupsForAction) {
        return buildRedisRestoreEvent(AuditStatus.SUCCESS, version, requiredGroupsForAction);
    }

    AuditPayload getRedisRestoreFailureEvent(String version, List<String> requiredGroupsForAction) {
        return buildRedisRestoreEvent(AuditStatus.FAILURE, version, requiredGroupsForAction);
    }

    private AuditPayload buildRedisRestoreEvent(AuditStatus auditStatus, String version, List<String> requiredGroupsForAction) {
        return createAuditPayloadBuilder(requiredGroupsForAction, auditStatus, REDIS_RESTORE_ACTION_ID)
                .action(AuditAction.UPDATE)
                .message(String.format(REDIS_RESTORE_MESSAGE, version))
                .resources(singletonList(dataPartitionId))
                .build();
    }

    // Group operations - Success/Failure paired methods
    AuditPayload getCreateGroupSuccessEvent(String groupId, List<String> requiredGroupsForAction) {
        return buildCreateGroupEvent(AuditStatus.SUCCESS, groupId, requiredGroupsForAction);
    }

    AuditPayload getCreateGroupFailureEvent(String groupId, List<String> requiredGroupsForAction) {
        return buildCreateGroupEvent(AuditStatus.FAILURE, groupId, requiredGroupsForAction);
    }

    private AuditPayload buildCreateGroupEvent(AuditStatus auditStatus, String groupId, List<String> requiredGroupsForAction) {
        return createAuditPayloadBuilder(requiredGroupsForAction, auditStatus, REDIS_CREATE_GROUP_TRANSACTION_ACTION_ID)
                .action(AuditAction.CREATE)
                .message(String.format(REDIS_CREATE_GROUP_TRANSACTION_MESSAGE, groupId))
                .resources(Arrays.asList(dataPartitionId, groupId))
                .build();
    }

    AuditPayload getUpdateGroupSuccessEvent(String groupId, List<String> requiredGroupsForAction) {
        return buildUpdateGroupEvent(AuditStatus.SUCCESS, groupId, requiredGroupsForAction);
    }

    AuditPayload getUpdateGroupFailureEvent(String groupId, List<String> requiredGroupsForAction) {
        return buildUpdateGroupEvent(AuditStatus.FAILURE, groupId, requiredGroupsForAction);
    }

    private AuditPayload buildUpdateGroupEvent(AuditStatus auditStatus, String groupId, List<String> requiredGroupsForAction) {
        return createAuditPayloadBuilder(requiredGroupsForAction, auditStatus, REDIS_UPDATE_GROUP_TRANSACTION_ACTION_ID)
                .action(AuditAction.UPDATE)
                .message(String.format(REDIS_UPDATE_GROUP_TRANSACTION_MESSAGE, groupId))
                .resources(Arrays.asList(dataPartitionId, groupId))
                .build();
    }

    AuditPayload getDeleteGroupSuccessEvent(String groupId, List<String> requiredGroupsForAction) {
        return buildDeleteGroupEvent(AuditStatus.SUCCESS, groupId, requiredGroupsForAction);
    }

    AuditPayload getDeleteGroupFailureEvent(String groupId, List<String> requiredGroupsForAction) {
        return buildDeleteGroupEvent(AuditStatus.FAILURE, groupId, requiredGroupsForAction);
    }

    private AuditPayload buildDeleteGroupEvent(AuditStatus auditStatus, String groupId, List<String> requiredGroupsForAction) {
        return createAuditPayloadBuilder(requiredGroupsForAction, auditStatus, REDIS_DELETE_GROUP_TRANSACTION_ACTION_ID)
                .action(AuditAction.DELETE)
                .message(String.format(REDIS_DELETE_GROUP_TRANSACTION_MESSAGE, groupId))
                .resources(Arrays.asList(dataPartitionId, groupId))
                .build();
    }

    // Member operations - Success/Failure paired methods
    AuditPayload getAddMemberSuccessEvent(String groupId, String memberId, Role role, List<String> requiredGroupsForAction) {
        return buildAddMemberEvent(AuditStatus.SUCCESS, groupId, memberId, role, requiredGroupsForAction);
    }

    AuditPayload getAddMemberFailureEvent(String groupId, String memberId, Role role, List<String> requiredGroupsForAction) {
        return buildAddMemberEvent(AuditStatus.FAILURE, groupId, memberId, role, requiredGroupsForAction);
    }

    private AuditPayload buildAddMemberEvent(AuditStatus auditStatus, String groupId, String memberId, Role role,
            List<String> requiredGroupsForAction) {
        return createAuditPayloadBuilder(requiredGroupsForAction, auditStatus, REDIS_ADD_MEMBER_TRANSACTION_ACTION_ID)
                .action(AuditAction.UPDATE)
                .message(String.format(REDIS_ADD_MEMBER_TRANSACTION_MESSAGE, memberId, groupId, role))
                .resources(Arrays.asList(dataPartitionId, groupId, memberId, role.toString()))
                .build();
    }

    AuditPayload getRemoveMemberSuccessEvent(String groupId, String memberId, String requesterId, List<String> requiredGroupsForAction) {
        return buildRemoveMemberEvent(AuditStatus.SUCCESS, groupId, memberId, requesterId, requiredGroupsForAction);
    }

    AuditPayload getRemoveMemberFailureEvent(String groupId, String memberId, String requesterId, List<String> requiredGroupsForAction) {
        return buildRemoveMemberEvent(AuditStatus.FAILURE, groupId, memberId, requesterId, requiredGroupsForAction);
    }

    private AuditPayload buildRemoveMemberEvent(AuditStatus auditStatus, String groupId, String memberId, String requesterId,
            List<String> requiredGroupsForAction) {
        return createAuditPayloadBuilder(requiredGroupsForAction, auditStatus, REDIS_REMOVE_MEMBER_TRANSACTION_ACTION_ID)
                .action(AuditAction.DELETE)
                .message(String.format(REDIS_REMOVE_MEMBER_TRANSACTION_MESSAGE, memberId, groupId, requesterId))
                .resources(Arrays.asList(dataPartitionId, groupId, memberId, requesterId))
                .build();
    }

    // Read operations - Success/Failure paired methods
    AuditPayload getListGroupSuccessEvent(List<String> groupIds, List<String> requiredGroupsForAction) {
        return buildListGroupEvent(AuditStatus.SUCCESS, groupIds, requiredGroupsForAction);
    }

    AuditPayload getListGroupFailureEvent(List<String> groupIds, List<String> requiredGroupsForAction) {
        return buildListGroupEvent(AuditStatus.FAILURE, groupIds, requiredGroupsForAction);
    }

    private AuditPayload buildListGroupEvent(AuditStatus auditStatus, List<String> groupIds, List<String> requiredGroupsForAction) {
        return createAuditPayloadBuilder(requiredGroupsForAction, auditStatus, REDIS_LIST_GROUP_TRANSACTION_ACTION_ID)
                .action(AuditAction.READ)
                .message(REDIS_LIST_GROUP_TRANSACTION_MESSAGE)
                .resources(Stream.concat(Collections.singletonList(dataPartitionId).stream(), groupIds.stream()).collect(Collectors.toList()))
                .build();
    }

    AuditPayload getListMemberSuccessEvent(String groupId, List<String> requiredGroupsForAction) {
        return buildListMemberEvent(AuditStatus.SUCCESS, groupId, requiredGroupsForAction);
    }

    AuditPayload getListMemberFailureEvent(String groupId, List<String> requiredGroupsForAction) {
        return buildListMemberEvent(AuditStatus.FAILURE, groupId, requiredGroupsForAction);
    }

    private AuditPayload buildListMemberEvent(AuditStatus auditStatus, String groupId, List<String> requiredGroupsForAction) {
        return createAuditPayloadBuilder(requiredGroupsForAction, auditStatus, REDIS_LIST_MEMBER_TRANSACTION_ACTION_ID)
                .action(AuditAction.READ)
                .message(String.format(REDIS_LIST_MEMBER_TRANSACTION_MESSAGE, groupId))
                .resources(Arrays.asList(dataPartitionId, groupId))
                .build();
    }

    // App ID operations - Success/Failure paired methods
    AuditPayload getUpdateAppIdsSuccessEvent(String groupId, Set<String> appIds, List<String> requiredGroupsForAction) {
        return buildUpdateAppIdsEvent(AuditStatus.SUCCESS, groupId, appIds, requiredGroupsForAction);
    }

    AuditPayload getUpdateAppIdsFailureEvent(String groupId, Set<String> appIds, List<String> requiredGroupsForAction) {
        return buildUpdateAppIdsEvent(AuditStatus.FAILURE, groupId, appIds, requiredGroupsForAction);
    }

    private AuditPayload buildUpdateAppIdsEvent(AuditStatus auditStatus, String groupId, Set<String> appIds,
            List<String> requiredGroupsForAction) {
        return createAuditPayloadBuilder(requiredGroupsForAction, auditStatus, REDIS_UPDATE_APP_IDS_TRANSACTION_ACTION_ID)
                .action(AuditAction.UPDATE)
                .message(String.format(REDIS_UPDATE_APP_IDS_TRANSACTION_MESSAGE, groupId, JsonConverter.toJson(appIds)))
                .resources(Arrays.asList(dataPartitionId, groupId))
                .build();
    }

    // Cache operations - Success/Failure paired methods
    AuditPayload getUpdateGroupsInCacheForKeysSuccessEvent(Set<String> keys, List<String> requiredGroupsForAction) {
        return buildUpdateGroupsInCacheForKeysEvent(AuditStatus.SUCCESS, keys, requiredGroupsForAction);
    }

    AuditPayload getUpdateGroupsInCacheForKeysFailureEvent(Set<String> keys, List<String> requiredGroupsForAction) {
        return buildUpdateGroupsInCacheForKeysEvent(AuditStatus.FAILURE, keys, requiredGroupsForAction);
    }

    private AuditPayload buildUpdateGroupsInCacheForKeysEvent(AuditStatus auditStatus, Set<String> keys,
            List<String> requiredGroupsForAction) {
        return createAuditPayloadBuilder(requiredGroupsForAction, auditStatus, REDIS_UPDATE_GROUPS_IN_CACHE_FOR_KEYS_ACTION_ID)
                .action(AuditAction.UPDATE)
                .message(REDIS_UPDATE_GROUPS_IN_CACHE_FOR_KEYS_MESSAGE)
                .resources(new ArrayList<>(keys))
                .build();
    }
}

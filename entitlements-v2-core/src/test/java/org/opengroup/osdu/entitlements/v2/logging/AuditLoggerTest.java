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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.http.RequestInfo;
import org.opengroup.osdu.entitlements.v2.model.Role;
import org.opengroup.osdu.entitlements.v2.util.RequestInfoUtilService;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(PowerMockRunner.class)
public class AuditLoggerTest {
    @Mock
    private JaxRsDpsLog log;

    @Mock
    private RequestInfo requestInfo;

    @Mock
    private RequestInfoUtilService requestInfoUtilService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AuditLogger auditLogger;

    private AuditEvents auditEvents;

    @Before
    public void setup() {
        auditEvents = new AuditEvents("user", "partitionid", "127.0.0.1", "Test-Agent", "users@partitionid.example.com");

        Map<String, String> headers = new HashMap<>();
        headers.put(DpsHeaders.DATA_PARTITION_ID, "partitionid");
        headers.put(DpsHeaders.CORRELATION_ID, "1234567890");
        headers.put(DpsHeaders.USER_AUTHORIZED_GROUP_NAME, "users@partitionid.example.com");
        DpsHeaders dpsHeaders = DpsHeaders.createFromMap(headers);
        when(requestInfo.getHeaders()).thenReturn(dpsHeaders);
        when(requestInfoUtilService.getUserId(dpsHeaders)).thenReturn("user");
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("Test-Agent");
    }

    // ==================== Redis Backup Tests ====================

    @Test
    public void shouldLogRedisInstanceBackupSuccess() {
        auditLogger.redisInstanceBackupSuccess();
        verify(log).audit(auditEvents.getRedisBackupSuccessEvent(AuditOperation.REDIS_BACKUP.getRequiredGroups()));
    }

    @Test
    public void shouldLogRedisInstanceBackupFailure() {
        auditLogger.redisInstanceBackupFailure();
        verify(log).audit(auditEvents.getRedisBackupFailureEvent(AuditOperation.REDIS_BACKUP.getRequiredGroups()));
    }

    @Test
    public void shouldLogRedisInstanceBackupVersionsSuccess() {
        auditLogger.redisInstanceBackupVersionsSuccess();
        verify(log).audit(auditEvents.getRedisBackupVersionSuccessEvent(AuditOperation.REDIS_BACKUP_VERSIONS.getRequiredGroups()));
    }

    @Test
    public void shouldLogRedisInstanceBackupVersionsFailure() {
        auditLogger.redisInstanceBackupVersionsFailure();
        verify(log).audit(auditEvents.getRedisBackupVersionFailureEvent(AuditOperation.REDIS_BACKUP_VERSIONS.getRequiredGroups()));
    }

    @Test
    public void shouldLogRedisInstanceRestoreSuccess() {
        auditLogger.redisInstanceRestoreSuccess("version");
        verify(log).audit(auditEvents.getRedisRestoreSuccessEvent("version", AuditOperation.REDIS_RESTORE.getRequiredGroups()));
    }

    @Test
    public void shouldLogRedisInstanceRestoreFailure() {
        auditLogger.redisInstanceRestoreFailure("version");
        verify(log).audit(auditEvents.getRedisRestoreFailureEvent("version", AuditOperation.REDIS_RESTORE.getRequiredGroups()));
    }

    // ==================== Group Operation Tests ====================

    @Test
    public void shouldLogCreateGroupSuccess() {
        auditLogger.createGroupSuccess("groupid");
        verify(log).audit(auditEvents.getCreateGroupSuccessEvent("groupid", AuditOperation.CREATE_GROUP.getRequiredGroups()));
    }

    @Test
    public void shouldLogCreateGroupFailure() {
        auditLogger.createGroupFailure("groupid");
        verify(log).audit(auditEvents.getCreateGroupFailureEvent("groupid", AuditOperation.CREATE_GROUP.getRequiredGroups()));
    }

    @Test
    public void shouldLogDeleteGroupSuccess() {
        auditLogger.deleteGroupSuccess("groupid");
        verify(log).audit(auditEvents.getDeleteGroupSuccessEvent("groupid", AuditOperation.DELETE_GROUP.getRequiredGroups()));
    }

    @Test
    public void shouldLogDeleteGroupFailure() {
        auditLogger.deleteGroupFailure("groupid");
        verify(log).audit(auditEvents.getDeleteGroupFailureEvent("groupid", AuditOperation.DELETE_GROUP.getRequiredGroups()));
    }

    @Test
    public void shouldLogUpdateGroupSuccess() {
        auditLogger.updateGroupSuccess("groupid");
        verify(log).audit(auditEvents.getUpdateGroupSuccessEvent("groupid", AuditOperation.UPDATE_GROUP.getRequiredGroups()));
    }

    @Test
    public void shouldLogUpdateGroupFailure() {
        auditLogger.updateGroupFailure("groupid");
        verify(log).audit(auditEvents.getUpdateGroupFailureEvent("groupid", AuditOperation.UPDATE_GROUP.getRequiredGroups()));
    }

    // ==================== Member Operation Tests ====================

    @Test
    public void shouldLogAddMemberSuccess() {
        auditLogger.addMemberSuccess("groupid", "memberid", Role.MEMBER);
        verify(log).audit(auditEvents.getAddMemberSuccessEvent("groupid", "memberid", Role.MEMBER, AuditOperation.ADD_MEMBER.getRequiredGroups()));
    }

    @Test
    public void shouldLogAddMemberFailure() {
        auditLogger.addMemberFailure("groupid", "memberid", Role.MEMBER);
        verify(log).audit(auditEvents.getAddMemberFailureEvent("groupid", "memberid", Role.MEMBER, AuditOperation.ADD_MEMBER.getRequiredGroups()));
    }

    @Test
    public void shouldLogRemoveMemberSuccess() {
        auditLogger.removeMemberSuccess("groupid", "memberid", "requestid");
        verify(log).audit(auditEvents.getRemoveMemberSuccessEvent("groupid", "memberid", "requestid", AuditOperation.REMOVE_MEMBER.getRequiredGroups()));
    }

    @Test
    public void shouldLogRemoveMemberFailure() {
        auditLogger.removeMemberFailure("groupid", "memberid", "requestid");
        verify(log).audit(auditEvents.getRemoveMemberFailureEvent("groupid", "memberid", "requestid", AuditOperation.REMOVE_MEMBER.getRequiredGroups()));
    }

    @Test
    public void shouldLogRemoveMemberSuccessWithDeleteMemberOperation() {
        auditLogger.removeMemberSuccess("groupid", "memberid", "requestid", AuditOperation.DELETE_MEMBER);
        verify(log).audit(auditEvents.getRemoveMemberSuccessEvent("groupid", "memberid", "requestid", AuditOperation.DELETE_MEMBER.getRequiredGroups()));
    }

    @Test
    public void shouldLogRemoveMemberFailureWithDeleteMemberOperation() {
        auditLogger.removeMemberFailure("groupid", "memberid", "requestid", AuditOperation.DELETE_MEMBER);
        verify(log).audit(auditEvents.getRemoveMemberFailureEvent("groupid", "memberid", "requestid", AuditOperation.DELETE_MEMBER.getRequiredGroups()));
    }

    // ==================== Read Operation Tests ====================

    @Test
    public void shouldLogListGroupSuccess() {
        auditLogger.listGroupSuccess(new ArrayList<>());
        verify(log).audit(auditEvents.getListGroupSuccessEvent(new ArrayList<>(), AuditOperation.LIST_GROUP.getRequiredGroups()));
    }

    @Test
    public void shouldLogListGroupFailure() {
        auditLogger.listGroupFailure(new ArrayList<>());
        verify(log).audit(auditEvents.getListGroupFailureEvent(new ArrayList<>(), AuditOperation.LIST_GROUP.getRequiredGroups()));
    }

    @Test
    public void shouldLogListMemberSuccess() {
        auditLogger.listMemberSuccess("groupid");
        verify(log).audit(auditEvents.getListMemberSuccessEvent("groupid", AuditOperation.LIST_MEMBER.getRequiredGroups()));
    }

    @Test
    public void shouldLogListMemberFailure() {
        auditLogger.listMemberFailure("groupid");
        verify(log).audit(auditEvents.getListMemberFailureEvent("groupid", AuditOperation.LIST_MEMBER.getRequiredGroups()));
    }

    // ==================== App ID Operation Tests ====================

    @Test
    public void shouldLogUpdateAppIdsSuccess() {
        Set<String> appIds = new HashSet<>();
        appIds.add("appid1");
        auditLogger.updateAppIdsSuccess("groupid", appIds);
        verify(log).audit(auditEvents.getUpdateAppIdsSuccessEvent("groupid", appIds, AuditOperation.UPDATE_APP_IDS.getRequiredGroups()));
    }

    @Test
    public void shouldLogUpdateAppIdsFailure() {
        Set<String> appIds = new HashSet<>();
        appIds.add("appid1");
        auditLogger.updateAppIdsFailure("groupid", appIds);
        verify(log).audit(auditEvents.getUpdateAppIdsFailureEvent("groupid", appIds, AuditOperation.UPDATE_APP_IDS.getRequiredGroups()));
    }
}

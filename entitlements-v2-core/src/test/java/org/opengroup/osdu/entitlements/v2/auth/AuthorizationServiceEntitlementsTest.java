package org.opengroup.osdu.entitlements.v2.auth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.entitlements.v2.model.ParentReference;
import org.opengroup.osdu.entitlements.v2.service.GroupsProvider;
import org.opengroup.osdu.entitlements.v2.util.RequestInfoUtilService;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class AuthorizationServiceEntitlementsTest {
    @Mock
    private JaxRsDpsLog log;
    @Mock
    private GroupsProvider groupsProvider;
    @Mock
    private RequestInfoUtilService requestInfoUtilService;
    @Mock
    private DpsHeaders headers;

    @InjectMocks
    private AuthorizationServiceEntitlements sut;

    @Before
    public void setup() {
        when(requestInfoUtilService.getUserId(any())).thenReturn("a@test.com");
        when(headers.getPartitionId()).thenReturn("dp");
    }

    @Test
    public void shouldReturnTrueWhenUserHasPermission() {
        ParentReference userGroupNode = ParentReference.builder().id("users@dp.domain.com").name("users").dataPartitionId("dp").build();
        ParentReference serviceGroupNode = ParentReference.builder().id("service.register.user@dp.domain.com").name("service.register.user").dataPartitionId("dp").build();
        when(groupsProvider.getGroupsInContext("a@test.com", "dp")).thenReturn(new HashSet<>(Arrays.asList(userGroupNode, serviceGroupNode)));

        assertTrue(sut.isCurrentUserAuthorized(headers, "service.register.user"));
    }

    @Test
    public void shouldReturnTrueWhenUserHasAnyPermission() {
        ParentReference userGroupNode = ParentReference.builder().id("users@dp.domain.com").name("users").dataPartitionId("dp").build();
        ParentReference serviceGroupNode = ParentReference.builder().id("service.register.user@dp.domain.com").name("service.register.user").dataPartitionId("dp").build();
        when(groupsProvider.getGroupsInContext("a@test.com", "dp")).thenReturn(new HashSet<>(Arrays.asList(userGroupNode, serviceGroupNode)));

        assertTrue(sut.isCurrentUserAuthorized(headers, "service.register.user", "service.register.editor"));
    }

    @Test
    public void shouldThrow401WhenUserDoesNotBelongToRootUserGroup() {
        ParentReference serviceGroupNode = ParentReference.builder().id("service.register.user@dp.domain.com").name("service.register.user").dataPartitionId("dp").build();
        when(groupsProvider.getGroupsInContext("akelham@bbc.com", "dp")).thenReturn(new HashSet<>(Collections.singletonList(serviceGroupNode)));

        try {
            sut.isCurrentUserAuthorized(headers, "service.register.user");
            fail("should throw exception");
        } catch (AppException ex) {
            assertEquals(401, ex.getError().getCode());
        } catch (Exception ex) {
            fail(String.format("should not throw exception: %s", ex));
        }
    }

    @Test
    public void shouldThrowUnauthorizedWhenUserDoesNotHaveRequiredPermission() {
        ParentReference userGroupNode = ParentReference.builder().id("users@dp.domain.com").name("users").dataPartitionId("dp").build();
        ParentReference serviceGroupNode = ParentReference.builder().id("service.register.user@dp.domain.com").name("service.register.user").dataPartitionId("dp").build();
        when(groupsProvider.getGroupsInContext("a@test.com", "dp")).thenReturn(new HashSet<>(Arrays.asList(userGroupNode, serviceGroupNode)));

        try {
            sut.isCurrentUserAuthorized(DpsHeaders.createFromMap(new HashMap<>()), "service.register.editor");
            fail("expected exception");
        } catch (AppException ex) {
            assertEquals(401, ex.getError().getCode());
        } catch (Exception ex) {
            fail(String.format("should not throw exception: %s", ex));
        }
    }
}

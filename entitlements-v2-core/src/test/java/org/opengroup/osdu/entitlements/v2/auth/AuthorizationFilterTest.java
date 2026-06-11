package org.opengroup.osdu.entitlements.v2.auth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.http.RequestInfo;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.entitlements.v2.AppProperties;
import org.opengroup.osdu.entitlements.v2.util.RequestInfoUtilService;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class AuthorizationFilterTest {

    private static final String ROLE1 = "role1";
    private static final String ROLE2 = "role2";
    private static final String SERVICE_PRINCIPAL = "service_principal";

    @Mock
    private DpsHeaders headers;
    @Mock
    private AuthorizationService authService;
    @Mock
    private RequestInfo requestInfo;
    @Mock
    private JaxRsDpsLog log;
    @Mock
    private RequestInfoUtilService requestInfoUtilService;

    @InjectMocks
    private AuthorizationFilter sut;

    @Before
    public void setup() {
        when(headers.getAuthorization()).thenReturn("Bearer token");
        when(requestInfo.getHeaders()).thenReturn(headers);
        when(headers.getPartitionId()).thenReturn("dp");
        TenantInfo tenantInfo = new TenantInfo();
        tenantInfo.setServiceAccount(SERVICE_PRINCIPAL);
        when(requestInfo.getTenantInfo()).thenReturn(tenantInfo);
    }

    @Test
    public void shouldThrow401WhenNoRolesArePassed() {
        when(requestInfoUtilService.getUserId(headers)).thenReturn("a@desid");
        try {
            sut.hasAnyPermission();
            fail("should throw exception");
        } catch (AppException ex) {
            assertEquals(401, ex.getError().getCode());
        } catch (Exception ex) {
            fail(String.format("should not throw exception: %s", ex));
        }
    }

    @Test
    public void shouldThrow403WhenDataPartitionIdIsUnknown() {
        when(requestInfo.getTenantInfo()).thenReturn(null);
        when(requestInfoUtilService.getUserId(headers)).thenReturn("a@desid");
        try {
            sut.hasAnyPermission();
            fail("should throw exception");
        } catch (AppException ex) {
            assertEquals(403, ex.getError().getCode());
            assertEquals("Invalid data partition id", ex.getError().getMessage());
        } catch (Exception ex) {
            fail(String.format("should not throw exception: %s", ex));
        }
    }

    @Test
    public void shouldAuthenticateRequestWhenRequestIsCalledByServicePrincipal() {
        when(requestInfoUtilService.getUserId(headers)).thenReturn(SERVICE_PRINCIPAL);

        assertTrue(this.sut.hasAnyPermission());
    }

    @Test
    public void shouldAuthenticateRequestWhenResourceIsRolesAllowedAnnotated() {
        when(requestInfoUtilService.getUserId(headers)).thenReturn("a@desid");
        when(this.authService.isCurrentUserAuthorized(any(), eq(ROLE1), eq(ROLE2))).thenReturn(true);

        assertTrue(this.sut.hasAnyPermission(ROLE1, ROLE2));
        verify(headers).put(DpsHeaders.USER_EMAIL, "a@desid");
    }

    @Test(expected = AppException.class)
    public void shouldThrowAppErrorWhenNoAuthzProvided() {
        when(requestInfoUtilService.getUserId(headers)).thenReturn("a@desid");
        when(headers.getAuthorization()).thenReturn("");

        this.sut.hasAnyPermission(ROLE1, ROLE2);
    }

    @Test
    public void shouldThrow403WhenRequesterDontHaveImpersonatorRole(){
        when(this.authService.isCurrentUserAuthorized(any(), eq(AppProperties.IMPERSONATOR))).thenReturn(false);
        try {
            this.sut.requesterHasImpersonationPermission(AppProperties.IMPERSONATOR);
        }catch (AppException e){
            assertEquals(403, e.getError().getCode());
        }
    }

    @Test
    public void shouldThrow403WhenImpersonationTargetDontHaveImpersonatedRole(){
        when(requestInfoUtilService.getImpersonationTarget(any())).thenReturn("user@desid");
        when(this.authService.isGivenUserAuthorized(any(), eq(AppProperties.IMPERSONATED_USER))).thenReturn(false);
        try {
            this.sut.targetCanBeImpersonated(AppProperties.IMPERSONATED_USER);
        }catch (AppException e){
            assertEquals(403, e.getError().getCode());
        }
    }

    @Test
    public void shouldThrow403WhenTenantServiceAccImpersonationAttempted(){
        when(requestInfoUtilService.getImpersonationTarget(headers)).thenReturn(SERVICE_PRINCIPAL);
        try {
            this.sut.targetCanBeImpersonated(AppProperties.IMPERSONATED_USER);
        }catch (AppException e){
            assertEquals(403, e.getError().getCode());
        }
    }
}

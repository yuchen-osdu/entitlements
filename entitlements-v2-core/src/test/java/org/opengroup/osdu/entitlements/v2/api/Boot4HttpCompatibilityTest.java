package org.opengroup.osdu.entitlements.v2.api;

import tools.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.entitlements.v2.auth.AuthorizationService;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.model.creategroup.CreateGroupServiceDto;
import org.opengroup.osdu.entitlements.v2.service.CreateGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards behaviour that changed with Spring Boot 4 / Spring Framework 7.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(controllers = CreateGroupApi.class)
@ComponentScan("org.opengroup.osdu.entitlements.v2")
public class Boot4HttpCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;
    @MockitoBean
    private CreateGroupService createGroupService;
    @MockitoBean
    private JaxRsDpsLog logger;
    @MockitoBean
    private ITenantFactory tenantFactory;
    @MockitoBean
    private AuthorizationService authService;

    @Before
    public void setup() {
        TenantInfo tenantInfo = new TenantInfo();
        tenantInfo.setServiceAccount("internal-service-account");
        when(tenantFactory.getTenantInfo("common")).thenReturn(tenantInfo);
        when(createGroupService.run(any(EntityNode.class), any(CreateGroupServiceDto.class)))
                .thenAnswer((Answer<EntityNode>) invocation -> invocation.getArgument(0));
        when(authService.isCurrentUserAuthorized(any(), any(String[].class))).thenReturn(true);
    }

    @Test
    public void shouldHandlePostWithTrailingSlashAndKeepBody() throws Exception {
        mockMvc.perform(post("/groups/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(DpsHeaders.AUTHORIZATION, "Bearer token")
                        .header(DpsHeaders.DATA_PARTITION_ID, "common")
                        .header(DpsHeaders.USER_ID, "a@b.com")
                        .content("{\"name\":\"service.viewers\",\"description\":\"desc\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<EntityNode> captor = ArgumentCaptor.forClass(EntityNode.class);
        verify(createGroupService).run(captor.capture(), any(CreateGroupServiceDto.class));
        assertThat(captor.getValue().getName()).isEqualTo("service.viewers");
        assertThat(captor.getValue().getDescription()).isEqualTo("desc");
    }

    @Test
    public void shouldUseApplicationJackson3MapperForHttpJson() {
        HttpMessageConverter<?> jsonConverter = handlerAdapter.getMessageConverters().stream()
                .filter(converter -> converter.getSupportedMediaTypes().contains(MediaType.APPLICATION_JSON))
                .findFirst()
                .orElseThrow();

        assertThat(jsonConverter).isInstanceOf(JacksonJsonHttpMessageConverter.class);
        assertThat(((JacksonJsonHttpMessageConverter) jsonConverter).getMapper()).isSameAs(objectMapper);
    }

    @Test
    public void shouldRejectMaliciousPathInSecurityFirewall() throws Exception {
        mockMvc.perform(get("/groups;jsessionid=x")
                        .header(DpsHeaders.AUTHORIZATION, "Bearer token")
                        .header(DpsHeaders.DATA_PARTITION_ID, "common")
                        .header(DpsHeaders.USER_ID, "a@b.com"))
                .andExpect(status().isBadRequest());
    }
}

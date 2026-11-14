package org.opengroup.osdu.entitlements.v2.swagger;

import io.swagger.v3.oas.models.Operation;
import org.junit.Test;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.openapi.OpenApiContractProperties;
import org.opengroup.osdu.core.common.openapi.web.AuthorizationHeaderEnforcementFilter;
import org.opengroup.osdu.entitlements.v2.api.HealthChecksApi;
import org.opengroup.osdu.entitlements.v2.api.InfoApi;
import org.opengroup.osdu.entitlements.v2.api.ListGroupOnBehalfOfApi;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DataPartitionHeaderOperationCustomizerTest {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> publicPaths = AuthorizationHeaderEnforcementFilter.DEFAULT_PUBLIC_PATHS;

    @Test
    public void skipsInfoEndpoint() throws Exception {
        HandlerMethod handler = new HandlerMethod(new InfoApi(), InfoApi.class.getMethod("info"));
        assertTrue(DataPartitionHeaderOperationCustomizer.matchesPublicPath(handler, publicPaths, pathMatcher));
        assertNull(customize(handler).getParameters());
    }

    @Test
    public void skipsHealthChecks() throws Exception {
        HandlerMethod liveness = new HandlerMethod(
                new HealthChecksApi(), HealthChecksApi.class.getMethod("livenessCheck"));
        HandlerMethod readiness = new HandlerMethod(
                new HealthChecksApi(), HealthChecksApi.class.getMethod("readinessCheck"));
        assertTrue(DataPartitionHeaderOperationCustomizer.matchesPublicPath(liveness, publicPaths, pathMatcher));
        assertTrue(DataPartitionHeaderOperationCustomizer.matchesPublicPath(readiness, publicPaths, pathMatcher));
    }

    @Test
    public void addsDataPartitionIdForProtectedApi() throws Exception {
        HandlerMethod handler = new HandlerMethod(
                new ListGroupOnBehalfOfApi(null, null, null),
                ListGroupOnBehalfOfApi.class.getMethod(
                        "listAllPartitionGroups", String.class, String.class, Integer.class));
        assertFalse(DataPartitionHeaderOperationCustomizer.matchesPublicPath(handler, publicPaths, pathMatcher));
        Operation operation = customize(handler);
        assertTrue(operation.getParameters().stream()
                .anyMatch(parameter -> DpsHeaders.DATA_PARTITION_ID.equals(parameter.getName())));
    }

    @Test
    public void usesConfiguredPublicPathsWhenPresent() {
        OpenApiContractProperties properties = new OpenApiContractProperties();
        properties.setPublicPaths(List.of("/custom-public"));
        assertTrue(DataPartitionHeaderOperationCustomizer.resolvePublicPaths(properties)
                .contains("/custom-public"));
    }

    private static Operation customize(HandlerMethod handlerMethod) {
        OperationCustomizer customizer = DataPartitionHeaderOperationCustomizer.create(null);
        return customizer.customize(new Operation(), handlerMethod);
    }
}

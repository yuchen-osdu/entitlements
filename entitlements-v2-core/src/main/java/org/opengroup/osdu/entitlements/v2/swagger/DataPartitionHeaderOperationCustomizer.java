package org.opengroup.osdu.entitlements.v2.swagger;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.openapi.OpenApiContractProperties;
import org.opengroup.osdu.core.common.openapi.web.AuthorizationHeaderEnforcementFilter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds required {@code data-partition-id} to every operation except public endpoints.
 * Public paths are taken from {@link OpenApiContractProperties#getPublicPaths()} (same list as
 * {@code osdu.openapi.contract} authorization enforcement), falling back to
 * {@link AuthorizationHeaderEnforcementFilter#DEFAULT_PUBLIC_PATHS}.
 */
final class DataPartitionHeaderOperationCustomizer {

    private DataPartitionHeaderOperationCustomizer() {
    }

    static OperationCustomizer create(OpenApiContractProperties contractProperties) {
        List<String> publicPaths = resolvePublicPaths(contractProperties);
        AntPathMatcher pathMatcher = new AntPathMatcher();
        return (operation, handlerMethod) -> {
            if (matchesPublicPath(handlerMethod, publicPaths, pathMatcher)) {
                return operation;
            }
            Parameter dataPartitionId = new Parameter()
                    .name(DpsHeaders.DATA_PARTITION_ID)
                    .description("Tenant Id")
                    .in("header")
                    .required(true)
                    .schema(new StringSchema());
            return operation.addParametersItem(dataPartitionId);
        };
    }

    static List<String> resolvePublicPaths(OpenApiContractProperties contractProperties) {
        if (contractProperties != null
                && contractProperties.getPublicPaths() != null
                && !contractProperties.getPublicPaths().isEmpty()) {
            return List.copyOf(contractProperties.getPublicPaths());
        }
        return AuthorizationHeaderEnforcementFilter.DEFAULT_PUBLIC_PATHS;
    }

    static boolean matchesPublicPath(
            HandlerMethod handlerMethod, List<String> publicPaths, AntPathMatcher pathMatcher) {
        for (String path : resolveHandlerPaths(handlerMethod)) {
            for (String pattern : publicPaths) {
                if (pathMatcher.match(pattern, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    static List<String> resolveHandlerPaths(HandlerMethod handlerMethod) {
        List<String> classPaths = classLevelPaths(handlerMethod.getBeanType());
        if (classPaths.isEmpty()) {
            classPaths = List.of("");
        }
        String[] methodPaths = mappingPaths(AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequestMapping.class));
        if (methodPaths.length == 0) {
            methodPaths = new String[]{""};
        }
        List<String> combined = new ArrayList<>();
        for (String classPath : classPaths) {
            for (String methodPath : methodPaths) {
                combined.add(joinPaths(classPath, methodPath));
            }
        }
        return combined;
    }

    private static List<String> classLevelPaths(Class<?> beanType) {
        List<String> paths = new ArrayList<>();
        for (String path : mappingPaths(AnnotatedElementUtils.findMergedAnnotation(beanType, RequestMapping.class))) {
            paths.add(path);
        }
        for (Class<?> iface : beanType.getInterfaces()) {
            for (String path : mappingPaths(AnnotatedElementUtils.findMergedAnnotation(iface, RequestMapping.class))) {
                paths.add(path);
            }
        }
        return paths;
    }

    private static String[] mappingPaths(RequestMapping mapping) {
        if (mapping == null) {
            return new String[0];
        }
        String[] paths = mapping.path();
        return paths.length > 0 ? paths : mapping.value();
    }

    private static String joinPaths(String classPath, String methodPath) {
        if (classPath == null || classPath.isEmpty()) {
            return normalize(methodPath);
        }
        if (methodPath == null || methodPath.isEmpty()) {
            return normalize(classPath);
        }
        String left = classPath.endsWith("/") ? classPath.substring(0, classPath.length() - 1) : classPath;
        String right = methodPath.startsWith("/") ? methodPath : "/" + methodPath;
        return normalize(left + right);
    }

    private static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}

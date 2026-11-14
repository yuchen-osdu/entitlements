package org.opengroup.osdu.entitlements.v2.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.entitlements.v2.model.creategroup.CreateGroupDto;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Serializes and reads every entitlements API model with the Jackson 2 mapper the service used before Spring Boot 4
 * and with the Jackson 3 mapper from {@link ConfigMapper}, so a changed Jackson default shows up as a changed wire
 * format. Property order is not compared: Jackson 3 sorts alphabetically, and JSON objects are unordered.
 */
class Jackson3ModelParityTest {

    private static final String MODEL_PACKAGE = "org.opengroup.osdu.entitlements.v2.model";
    private static final int MAX_DEPTH = 3;

    private final ObjectMapper jackson2 = new ObjectMapper()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final JsonMapper jackson3 = new ConfigMapper().objectMapper();

    @Test
    void every_model_should_have_the_same_wire_format_on_both_jackson_versions() throws Exception {
        List<String> differences = new ArrayList<>();
        int compared = 0;

        Set<Class<?>> models = modelClasses();
        models.add(AppError.class);
        for (Class<?> model : models) {
            Object instance = populatedInstance(model, 0);
            if (instance == null) {
                continue;
            }
            String jackson2Json;
            try {
                jackson2Json = jackson2.writeValueAsString(instance);
            } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException _) {
                continue; // Not a JSON model under Jackson 2 either.
            }
            compared++;

            String jackson3Json;
            try {
                jackson3Json = jackson3.writeValueAsString(instance);
            } catch (RuntimeException e) {
                differences.add(model.getName() + " does not serialize: " + e.getMessage());
                continue;
            }
            if (!jackson2.readTree(jackson2Json).equals(jackson2.readTree(jackson3Json))) {
                differences.add(model.getName() + "\n  jackson2: " + jackson2Json + "\n  jackson3: " + jackson3Json);
                continue;
            }
            String readBack = readBackDifference(model, jackson2Json);
            if (readBack != null) {
                differences.add(readBack);
            }
        }

        assertTrue(compared > 30, "Expected to compare the model classes, but only compared " + compared);
        assertEquals(List.of(), differences, String.join("\n", differences));
    }

    @Test
    void request_parsing_should_stay_lenient_like_jackson_2() throws Exception {
        String json = "{\"NAME\":\"users.x\",\"Description\":null,\"unknown\":1}";
        assertEquals(jackson2.writeValueAsString(jackson2.readValue(json, CreateGroupDto.class)),
                jackson2.writeValueAsString(jackson3.readValue(json, CreateGroupDto.class)));
    }

    // Reads the same JSON with both mappers and compares what each one populated.
    private String readBackDifference(Class<?> model, String json) {
        Object jackson2Value;
        try {
            jackson2Value = jackson2.readValue(json, model);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException _) {
            return null; // Write-only under Jackson 2, so there is no read behaviour to preserve.
        }
        try {
            Object jackson3Value = jackson3.readValue(json, model);
            String expected = jackson2.writeValueAsString(jackson2Value);
            String actual = jackson2.writeValueAsString(jackson3Value);
            return jackson2.readTree(expected).equals(jackson2.readTree(actual)) ? null
                    : model.getName() + " reads differently\n  jackson2: " + expected + "\n  jackson3: " + actual;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException e) {
            return model.getName() + " does not deserialize: " + e.getMessage();
        }
    }

    private static Set<Class<?>> modelClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        Set<Class<?>> models = new HashSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(MODEL_PACKAGE)) {
            models.add(Class.forName(definition.getBeanClassName()));
        }
        return models;
    }

    private static Object populatedInstance(Class<?> model, int depth) {
        if (depth > MAX_DEPTH) {
            return null;
        }
        if (model.isInterface() || model.isEnum() || Modifier.isAbstract(model.getModifiers())) {
            return null;
        }
        try {
            // Most models are Lombok @Builder classes, so use the widest constructor and then fill the fields.
            Constructor<?> constructor = Arrays.stream(model.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount))
                    .orElseThrow();
            constructor.setAccessible(true);
            Parameter[] parameters = constructor.getParameters();
            Object[] arguments = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                arguments[i] = sample(parameters[i].getType(), parameters[i].getParameterizedType(), depth);
                if (arguments[i] == null && parameters[i].getType().isPrimitive()) {
                    return null;
                }
            }
            Object instance = constructor.newInstance(arguments);
            for (Class<?> type = model; type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    populate(instance, field, depth);
                }
            }
            return instance;
        } catch (ReflectiveOperationException | RuntimeException _) {
            return null;
        }
    }

    private static void populate(Object instance, Field field, int depth) throws IllegalAccessException {
        if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
            return;
        }
        Object value = sample(field.getType(), field.getGenericType(), depth);
        if (value != null) {
            field.setAccessible(true);
            field.set(instance, value);
        }
    }

    private static Object sample(Class<?> type, Type genericType, int depth) {
        if (type == String.class) {
            return "value";
        }
        if (type == int.class || type == Integer.class) {
            return 7;
        }
        if (type == long.class || type == Long.class) {
            return 7L;
        }
        if (type == double.class || type == Double.class) {
            return 1.5d;
        }
        if (type == boolean.class || type == Boolean.class) {
            return true;
        }
        if (type == java.util.Date.class) {
            return new java.util.Date(1893456000000L);
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length == 0 ? null : constants[0];
        }
        if (genericType instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (Map.class.isAssignableFrom(type) && arguments[0] == String.class) {
                Map<String, Object> map = new HashMap<>();
                map.put("key", "value");
                map.put("nullValue", null);
                return map;
            }
            Object element = arguments[0] instanceof Class<?> elementType
                    ? sample(elementType, elementType, depth + 1) : null;
            if (element == null) {
                return null;
            }
            if (List.class.isAssignableFrom(type)) {
                return new ArrayList<>(List.of(element));
            }
            if (Set.class.isAssignableFrom(type)) {
                return element instanceof Comparable<?> ? new TreeSet<>(Set.of(element)) : new HashSet<>(Set.of(element));
            }
            return null;
        }
        if (type.getName().startsWith(MODEL_PACKAGE)) {
            return populatedInstance(type, depth + 1);
        }
        return null;
    }
}

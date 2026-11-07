package org.opengroup.osdu.entitlements.v2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class ConfigMapper {

    /**
     * Declared as {@link JsonMapper} so it replaces Spring Boot's auto-configured mapper and is used for HTTP
     * message conversion. The settings keep the request contract of the former Jackson 2 mapper.
     */
    @Bean
    @Primary
    public JsonMapper objectMapper() {
        return JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Jackson 2 read null for a primitive as its default value; Jackson 3 would reject it with a 400.
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }
}

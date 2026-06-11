package org.opengroup.osdu.entitlements.v2.acceptance.util;

import java.io.InputStream;
import java.util.Properties;

public interface ConfigurationService {

    String getTenantId();

    String getServiceUrl();

    String getDomain();

    String getIdOfGroup(String groupName);

    default boolean isFeatureFlagEnabled(String flagName) {
        try {
            Properties properties = new Properties();
            try (InputStream input = this.getClass().getClassLoader().getResourceAsStream("test.properties")) {
                if (input != null) {
                    properties.load(input);
                    String propValue = properties.getProperty(flagName, null);
                    if (propValue != null) {
                        return Boolean.parseBoolean(propValue);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to read feature flag from test.properties: " + e.getMessage());
        }
        
        // Fallback to environment variable for disable-data-root-group-hierarchy
        if ("disable-data-root-group-hierarchy".equals(flagName)) {
            String envValue = System.getenv("DATA_ROOT_GROUP_HIERARCHY_ENABLED");
            if (envValue != null) {
                // Invert the logic: if hierarchy is enabled, then disable flag is false
                return !Boolean.parseBoolean(envValue);
            }
        }
        
        // Default to false
        return false;
    }

    default String getMemberMailId() {
        return "testMember@test.com";
    }

    default String getOwnerMailId() {
        return "testmMemberOwner@test.com";
    }

    default String getMemberMailId_toBeDeleted(long timestamp) {
        return String.format("testMember-%s@test.com", timestamp);
    }
}

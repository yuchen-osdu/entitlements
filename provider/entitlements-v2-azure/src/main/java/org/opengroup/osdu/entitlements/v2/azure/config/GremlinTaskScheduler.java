package org.opengroup.osdu.entitlements.v2.azure.config;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Task scheduler dedicated to Gremlin token refresh and retry work.
 */
@Component
public class GremlinTaskScheduler extends ThreadPoolTaskScheduler {

    @PostConstruct
    public void initialize() {
        setPoolSize(1);
        setThreadNamePrefix("gremlin-refresh-");
        setAwaitTerminationSeconds(30);
        setWaitForTasksToCompleteOnShutdown(true);
        super.initialize();
    }
}

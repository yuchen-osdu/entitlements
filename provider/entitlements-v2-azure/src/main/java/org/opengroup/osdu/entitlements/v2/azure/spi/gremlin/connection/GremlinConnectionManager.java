package org.opengroup.osdu.entitlements.v2.azure.spi.gremlin.connection;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.util.ser.GraphSONMessageSerializerV2;
import org.opengroup.osdu.azure.util.AzureServicePrincipal;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.entitlements.v2.azure.AzureAppProperties;
import org.opengroup.osdu.entitlements.v2.azure.config.GremlinTaskScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

/**
 * Manages Gremlin connections using Cosmos DB Microsoft Entra tokens.
 */
@Component
@RequiredArgsConstructor
@Log
public class GremlinConnectionManager {

    private static final int MAX_CONTENT_LENGTH = 65536;
    private static final long KEEP_ALIVE_TIME = 30000;
    private static final int MAX_IN_PROCESS = 16;
    private static final String COSMOS_DB_ENDPOINT = "https://cosmos.azure.com/.default";
    private static final int INITIAL_RETRY_SECONDS = 30;
    private static final int MAX_RETRY_SECONDS = 1800;

    private final AzureServicePrincipal azureServicePrincipal;
    private final AzureAppProperties config;
    private final GremlinTaskScheduler gremlinTaskScheduler;

    private volatile Cluster cluster;
    private volatile Client client;
    private volatile GraphTraversalSource graphTraversalSource;

    private ScheduledFuture<?> refreshTask;
    private volatile int retryAttempt = 0;

    @PostConstruct
    public void initialize() {
        log.info("Initializing Gremlin Connection Manager");
        refreshConnections();
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroying Gremlin Connection Manager");
        cancelScheduledRefresh();
        closeConnections();
    }

    public void refreshConnections() {
        log.info("Refreshing Gremlin connections with MSI authentication...");

        try {
            closeConnections();
            refreshConnectionsWithMSI();
        } catch (Exception e) {
            log.severe("Failed to refresh Gremlin connections: " + e.getMessage());
            throw new AppException(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Failed to refresh Gremlin connections", e);
        }
    }

    public Cluster getCluster() {
        Cluster currentCluster = cluster;
        if (currentCluster == null) {
            throw new AppException(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "Service temporarily unavailable during periodic connection refresh. Please retry in a few seconds.");
        }
        return currentCluster;
    }

    public Client getClient() {
        Client currentClient = client;
        if (currentClient == null) {
            throw new AppException(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "Service temporarily unavailable during periodic connection refresh. Please retry in a few seconds.");
        }
        return currentClient;
    }

    public GraphTraversalSource getGraphTraversalSource() {
        GraphTraversalSource currentSource = graphTraversalSource;
        if (currentSource == null) {
            throw new AppException(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "Service temporarily unavailable during periodic connection refresh. Please retry in a few seconds.");
        }
        return currentSource;
    }

    private void refreshConnectionsWithMSI() {
        log.info("Using MSI authentication for Gremlin connection");

        try {
            String msiToken = this.azureServicePrincipal.getMSIToken(COSMOS_DB_ENDPOINT);
            log.info("Retrieved MSI token for Gremlin connection");

            Cluster newCluster = buildClusterWithMSI(msiToken);
            Client newClient = buildClient(newCluster);
            GraphTraversalSource newGraphTraversalSource = buildGraphTraversalSource(newCluster);

            this.cluster = newCluster;
            this.client = newClient;
            this.graphTraversalSource = newGraphTraversalSource;
            this.retryAttempt = 0;

            log.info("Successfully established MSI connections");
            scheduleNextRefresh(msiToken);
        } catch (Exception e) {
            log.severe("MSI connection failed: " + e.getMessage());
            scheduleMSIRetry(e);
        }
    }

    private void scheduleMSIRetry(Exception msiError) {
        retryAttempt++;

        int delaySeconds = retryAttempt > 6 ? MAX_RETRY_SECONDS : INITIAL_RETRY_SECONDS * (1 << (retryAttempt - 1));

        log.info("MSI connection failed (attempt " + retryAttempt + "). Scheduling retry in " + formatDuration(delaySeconds * 1000L) + "...");

        Instant retryTime = Instant.now().plusSeconds(delaySeconds);
        refreshTask = gremlinTaskScheduler.schedule(() -> {
            try {
                refreshConnectionsWithMSI();
            } catch (Exception retryException) {
                log.severe("MSI retry failed: " + retryException.getMessage());
                scheduleMSIRetry(retryException);
            }
        }, retryTime);

        log.info("Scheduled MSI retry #" + retryAttempt + " at: " + retryTime + " (delay: " + formatDuration(delaySeconds * 1000L) + ")");
    }

    private Cluster buildClusterWithMSI(String msiToken) {
        try {
            try {
                DecodedJWT jwt = JWT.decode(msiToken);
                long expiresAt = jwt.getExpiresAt().getTime();
                long currentTime = System.currentTimeMillis();
                log.info("JWT token issued at: " + jwt.getIssuedAt().getTime()
                    + ", expires at: " + expiresAt + " (in " + formatDuration(expiresAt - currentTime) + ")");
            } catch (Exception e) {
                log.severe("Failed to parse JWT token: " + e.getMessage());
            }

            return Cluster.build(getHost(this.config.getGraphDbEndpoint()))
                    .port(this.config.getGraphDbPort())
                    .credentials(this.config.getGraphDbUsername(), msiToken)
                    .enableSsl(this.config.isGraphDbSslEnabled())
                    .maxSimultaneousUsagePerConnection(MAX_IN_PROCESS)
                    .maxInProcessPerConnection(MAX_IN_PROCESS)
                    .maxContentLength(MAX_CONTENT_LENGTH)
                    .serializer(new GraphSONMessageSerializerV2())
                    .keepAliveInterval(KEEP_ALIVE_TIME)
                    .create();
        } catch (IllegalArgumentException e) {
            throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                    "Invalid configuration of Gremlin cluster with MSI", e);
        }
    }

    private Client buildClient(Cluster cluster) throws Exception {
        Client client = cluster.connect().alias("g");
        client.init();
        return client;
    }

    private GraphTraversalSource buildGraphTraversalSource(Cluster cluster) {
        return AnonymousTraversalSource.traversal().withRemote(DriverRemoteConnection.using(cluster));
    }

    private String getHost(String graphDbEndpoint) {
        return graphDbEndpoint.replace("https://", "")
                .replace(":443/", "")
                .replace("documents.azure.com", "gremlin.cosmos.azure.com");
    }

    private void closeConnections() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warning("Error closing Gremlin client: " + e.getMessage());
            }
            client = null;
        }

        if (cluster != null) {
            try {
                cluster.close();
            } catch (Exception e) {
                log.warning("Error closing Gremlin cluster: " + e.getMessage());
            }
            cluster = null;
        }

        graphTraversalSource = null;
    }

    private void scheduleNextRefresh(String token) {
        try {
            cancelScheduledRefresh();

            DecodedJWT decodedToken = JWT.decode(token);
            Date expiresAt = decodedToken.getExpiresAt();
            if (expiresAt == null) {
                log.warning("JWT token has no expiration date, falling back to fixed refresh schedule");
                Duration fiftyMinutes = Duration.ofMinutes(50);
                refreshTask = gremlinTaskScheduler.scheduleAtFixedRate(this::refreshConnections, fiftyMinutes);
                return;
            }

            long currentTime = System.currentTimeMillis();
            long expiryTime = expiresAt.getTime();
            long refreshTime = expiryTime - (config.getGremlinTokenRefreshBufferSeconds() * 1000);
            long delayMs = refreshTime - currentTime;

            if (delayMs < 3000) {
                delayMs = 3000;
                log.warning("Token expires very soon, scheduling refresh in 3 seconds");
            }
            Date refreshDate = new Date(currentTime + delayMs);
            log.info("Token expires at: " + expiresAt + " (in " + formatDuration(expiryTime - currentTime) + "), "
                + "scheduling refresh at: " + refreshDate + " (in " + formatDuration(delayMs) + ", "
                + config.getGremlinTokenRefreshBufferSeconds() + "s before expiry)");

            Instant refreshInstant = refreshDate.toInstant();
            refreshTask = gremlinTaskScheduler.schedule(this::refreshConnections, refreshInstant);
            log.info("Successfully scheduled refresh task");
        } catch (Exception e) {
            log.severe("Failed to schedule token refresh: " + e.getMessage());
            log.info("Falling back to 50-minute fixed refresh schedule");
            Duration fiftyMinutes = Duration.ofMinutes(50);
            refreshTask = gremlinTaskScheduler.scheduleAtFixedRate(this::refreshConnections, fiftyMinutes);
        }
    }

    private String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + "m " + seconds + "s";
    }

    private void cancelScheduledRefresh() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel(false);
            log.info("Cancelled scheduled refresh task");
        }
    }
}

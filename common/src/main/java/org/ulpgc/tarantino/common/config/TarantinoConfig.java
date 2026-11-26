package org.ulpgc.tarantino.common.config;

import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.common.model.WorkerIdentity;
import org.ulpgc.tarantino.common.roles.RoleNames;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Every {@code TARANTINO_*} variable of SPEC §4.7 with its default, read and validated at startup in every service.
 * Anything invalid throws {@link InvalidConfigurationException} naming the variable, and the service does not start.
 */
public record TarantinoConfig(
        int port,
        OptionalInt httpThreads,
        String mongoUri,
        List<String> hazelcastMembers,
        String hazelcastCluster,
        Path datalake,
        Path workload,
        Optional<Path> mirror,
        String candidates,
        int parallelDownloads,
        int maxAttempts,
        int indexBatch,
        Duration batchTimeout,
        Duration lease,
        Duration heartbeat,
        Duration reconcile,
        Duration requeue,
        Duration gc,
        Duration tmpMaxAge,
        Duration cleanup,
        Duration snapshot,
        String corsOrigin,
        Optional<CrashPoint> crashAt,
        IndexDesign indexDesign,
        String node,
        String workerId,
        Optional<String> advertiseHost,
        List<String> roles,
        int hazelcastPort,
        Duration hazelcastFailure,
        Duration workerHeartbeat,
        Duration workerTimeout,
        Duration peerTimeout) {

    /** The {@code TARANTINO_CANDIDATES} value that seeds every book of the local mirror. */
    public static final String MIRROR_CANDIDATES = "mirror";
    /** The Mongo URI variable, which {@code bench explain} also reads, and its default. */
    public static final String MONGO_URI = "TARANTINO_MONGO_URI";
    public static final String DEFAULT_MONGO_URI = "mongodb://localhost:27017";
    /** The snapshot refresh variable, which {@code checks} also reads to time its waits, and its default. */
    public static final String SNAPSHOT_SECONDS = "TARANTINO_SNAPSHOT_SECONDS";
    public static final int DEFAULT_SNAPSHOT_SECONDS = 5;

    private static final String FIRST_WORKER_OF_NODE = "-w1";
    private static final int MIN_LEASES_BEFORE_DELETING = 10;
    private static final int MIN_HEARTBEATS_BEFORE_EXPIRY = 3;

    public static TarantinoConfig fromEnvironment(TarantinoService service) {
        return from(System.getenv(), service);
    }

    public static TarantinoConfig from(Map<String, String> environment, TarantinoService service) {
        Variables variables = new Variables(environment);
        String node = variables.optionalText("TARANTINO_NODE").orElseGet(() -> LocalHost.name(environment));
        TarantinoConfig config = new TarantinoConfig(
                variables.positive("TARANTINO_PORT", service.defaultPort()),
                variables.optionalPositive("TARANTINO_HTTP_THREADS"),
                variables.text(MONGO_URI, DEFAULT_MONGO_URI),
                variables.list("TARANTINO_HAZELCAST", "localhost:5701"),
                variables.text("TARANTINO_HAZELCAST_CLUSTER", HazelcastContracts.DEFAULT_CLUSTER),
                Path.of(variables.text("TARANTINO_DATALAKE", "datalake")),
                Path.of(variables.text("TARANTINO_WORKLOAD", "workload")),
                variables.optionalText("TARANTINO_MIRROR").map(Path::of),
                variables.text("TARANTINO_CANDIDATES", "sample_ids.txt"),
                variables.positive("TARANTINO_PARALLEL_DOWNLOADS", 8),
                variables.positive("TARANTINO_MAX_ATTEMPTS", 3),
                variables.positive("TARANTINO_INDEX_BATCH", 100),
                variables.seconds("TARANTINO_BATCH_TIMEOUT_SECONDS", 30),
                variables.seconds("TARANTINO_LEASE_SECONDS", 60),
                variables.seconds("TARANTINO_HEARTBEAT_SECONDS", 20),
                variables.seconds("TARANTINO_RECONCILE_SECONDS", 30),
                variables.seconds("TARANTINO_REQUEUE_SECONDS", 120),
                variables.seconds("TARANTINO_GC_SECONDS", 600),
                variables.seconds("TARANTINO_TMP_MAX_AGE_SECONDS", 600),
                variables.seconds("TARANTINO_CLEANUP_SECONDS", 3600),
                variables.seconds(SNAPSHOT_SECONDS, DEFAULT_SNAPSHOT_SECONDS),
                variables.text("TARANTINO_CORS_ORIGIN", "*"),
                variables.optionalText("TARANTINO_CRASH_AT").map(point -> crashPoint(service, point)),
                indexDesign(variables.text("TARANTINO_INDEX_DESIGN", IndexDesign.LOCAL.variableValue())),
                node,
                variables.text("TARANTINO_WORKER_ID", node + FIRST_WORKER_OF_NODE),
                variables.optionalText("TARANTINO_ADVERTISE_HOST"),
                variables.listOrEmpty("TARANTINO_ROLES", String.join(",", RoleNames.ALL)),
                variables.positive("TARANTINO_HAZELCAST_PORT", 5701),
                variables.seconds("TARANTINO_HAZELCAST_FAILURE_SECONDS", 10),
                variables.seconds("TARANTINO_WORKER_HEARTBEAT_SECONDS", 2),
                variables.seconds("TARANTINO_WORKER_TIMEOUT_SECONDS", 10),
                variables.seconds("TARANTINO_PEER_TIMEOUT_SECONDS", 5));
        config.checkInvariants();
        return config;
    }

    /** Who this worker is in the registry and in every log line (SPEC §13.1). */
    public WorkerIdentity identity() {
        return new WorkerIdentity(workerId, node);
    }

    /** {@code TARANTINO_ADVERTISE_HOST}, or else the IPv4 address of this process's host name (SPEC §4.7). */
    public String advertisedHost() {
        return advertiseHost.orElseGet(LocalHost::address);
    }

    private void checkInvariants() {
        require(heartbeat.multipliedBy(MIN_HEARTBEATS_BEFORE_EXPIRY).compareTo(lease) <= 0,
                "TARANTINO_HEARTBEAT_SECONDS", "must be at most TARANTINO_LEASE_SECONDS / 3");
        require(workerHeartbeat.multipliedBy(MIN_HEARTBEATS_BEFORE_EXPIRY).compareTo(workerTimeout) <= 0,
                "TARANTINO_WORKER_HEARTBEAT_SECONDS", "must be at most TARANTINO_WORKER_TIMEOUT_SECONDS / 3");
        require(RoleNames.ALL.containsAll(roles) && roles.stream().distinct().count() == roles.size(),
                "TARANTINO_ROLES", "must hold only crawler, indexer and query, without repeats");
        require(gc.compareTo(lease.multipliedBy(MIN_LEASES_BEFORE_DELETING)) >= 0,
                "TARANTINO_GC_SECONDS", "must be at least 10 × TARANTINO_LEASE_SECONDS");
        require(tmpMaxAge.compareTo(lease.multipliedBy(MIN_LEASES_BEFORE_DELETING)) >= 0,
                "TARANTINO_TMP_MAX_AGE_SECONDS", "must be at least 10 × TARANTINO_LEASE_SECONDS");
        require(requeue.compareTo(batchTimeout) > 0,
                "TARANTINO_REQUEUE_SECONDS", "must be greater than TARANTINO_BATCH_TIMEOUT_SECONDS");
        require(!MIRROR_CANDIDATES.equals(candidates) || mirror.isPresent(),
                "TARANTINO_CANDIDATES", "can only be mirror with TARANTINO_MIRROR set");
    }

    private static void require(boolean holds, String variable, String reason) {
        if (!holds) {
            throw new InvalidConfigurationException(variable, reason);
        }
    }

    private static IndexDesign indexDesign(String value) {
        return IndexDesign.of(value).orElseThrow(() -> new InvalidConfigurationException("TARANTINO_INDEX_DESIGN",
                "must be local or grid, not " + value));
    }

    private static CrashPoint crashPoint(TarantinoService service, String point) {
        return CrashPoint.of(service, point).orElseThrow(() -> new InvalidConfigurationException("TARANTINO_CRASH_AT",
                "must be a point of " + service.name().toLowerCase(Locale.ROOT) + " in SPEC §8, not " + point));
    }
}

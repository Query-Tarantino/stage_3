package org.ulpgc.tarantino.worker;

import io.javalin.Javalin;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.hazelcast.EmbeddedMember;
import org.ulpgc.tarantino.common.hazelcast.HazelcastMembers;
import org.ulpgc.tarantino.common.model.WorkerRegistration;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoIndexes;
import org.ulpgc.tarantino.common.mongo.WorkerRegistry;
import org.ulpgc.tarantino.common.roles.Role;
import org.ulpgc.tarantino.common.roles.RoleNames;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.startup.Startup;
import org.ulpgc.tarantino.common.startup.WorkerThreads;
import org.ulpgc.tarantino.crawler.CrawlerFactory;
import org.ulpgc.tarantino.indexer.IndexerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * One {@code worker} process (SPEC §2.2 and §13): its embedded Hazelcast member, the HTTP routes of every role, its
 * registration and heartbeat in {@code workers}, and the roles of {@code TARANTINO_ROLES}, started when it starts.
 */
public final class Worker implements AutoCloseable {

    private static final String HEARTBEAT_THREAD = "assignment";
    private static final String UNKNOWN_COMMIT = "-";
    private static final int JAVALIN_MAX_THREADS = 250;
    private static final int BATCH_LOOPS = 1;

    private final EmbeddedMember member;
    private final Javalin app;
    private final Map<String, Role> roles;
    private final ScheduledExecutorService heartbeat;

    private Worker(EmbeddedMember member, Javalin app, Map<String, Role> roles, ScheduledExecutorService heartbeat) {
        this.member = member;
        this.app = app;
        this.roles = roles;
        this.heartbeat = heartbeat;
    }

    /** Starts the member in the background, opens the HTTP port, registers the worker and starts its first roles (SPEC §2.4 and §13.2). */
    public static Worker start(TarantinoConfig config) {
        EmbeddedMember member = HazelcastMembers.start(config);
        Connections connections = new Connections(config.mongoUri(), MongoDatabases.database(config.mongoUri()), member.broker());
        Javalin app = Startup.start(config, connections, List.of(),
                CrawlerFactory.routes(connections).andThen(IndexerFactory.routes(connections)));
        Startup.retry("create the book state indexes", () -> MongoIndexes.ensureStateIndexes(connections.database()));
        Map<String, Role> roles = Map.of(RoleNames.INDEXER, IndexerFactory.role(config, connections));
        WorkerRegistry registry = new WorkerRegistry(connections.database());
        WorkerRegistration registration = registration(config);
        Startup.retry("register the worker", () -> registry.register(registration));
        config.roles().stream().map(roles::get).filter(Objects::nonNull).forEach(Role::start);
        return new Worker(member, app, roles, beating(config, new RoleHeartbeat(registry, registration, roles.values())));
    }

    @Override
    public void close() {
        heartbeat.shutdownNow();
        roles.values().forEach(Role::stop);
        app.stop();
        member.shutdown();
    }

    private static WorkerRegistration registration(TarantinoConfig config) {
        Map<String, Integer> threads = Map.of("http", config.httpThreads().orElse(JAVALIN_MAX_THREADS),
                "downloads", config.parallelDownloads(), "index", BATCH_LOOPS);
        return new WorkerRegistration(config.identity(), config.advertisedHost(), config.port(), config.indexDesign().variableValue(),
                threads, UNKNOWN_COMMIT, config.roles());
    }

    /** “Beat as a worker” every {@code TARANTINO_WORKER_HEARTBEAT_SECONDS} on the thread {@value #HEARTBEAT_THREAD}. */
    private static ScheduledExecutorService beating(TarantinoConfig config, RoleHeartbeat beat) {
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name(HEARTBEAT_THREAD).factory());
        heartbeat.scheduleWithFixedDelay(WorkerThreads.exitingIfItDies(beat), 0, config.workerHeartbeat().toMillis(), TimeUnit.MILLISECONDS);
        return heartbeat;
    }
}

package org.ulpgc.tarantino.worker;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.model.WorkerAddress;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.mongo.WorkerRegistry;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerTest {

    private static final String LOOPBACK = "127.0.0.1";
    private static final Duration DEADLINE = Duration.ofSeconds(20);
    private static final Duration BEAT = Duration.ofSeconds(1);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @TempDir
    Path datalake;

    private Worker worker;

    @AfterEach
    void stop() {
        if (worker != null) {
            worker.close();
        }
    }

    @Test
    void startsItsFirstRolesAndBeatsInTheRegistryWithItsAddress() throws Exception {
        int port = freePort();
        worker = Worker.start(config(port));

        assertEquals(200, health(port));
        assertEquals(List.of(new WorkerAddress("n1-w1", "n1", LOOPBACK, port)),
                new WorkerRegistry(MongoDatabases.database(mongo.uri())).alive(Duration.ofSeconds(10)));
        assertTrue(within(() -> List.of("indexer").equals(registered().getList(MongoFields.ROLES_ACTIVE, String.class))));
    }

    @Test
    void registersAgainOnItsNextBeatWhenItsDocumentIsGone() throws Exception {
        worker = Worker.start(config(freePort()));

        workers().deleteMany(new Document());

        assertTrue(within(() -> registered() != null));
    }

    @Test
    void startsTheRolesAssignedSinceItRegisteredBeforeNotThoseOfItsVariable() throws Exception {
        workers().insertOne(new Document(MongoFields.ID, "n1-w1").append(MongoFields.ROLES_ASSIGNED, List.of())
                .append(MongoFields.ASSIGNMENT, 2));

        worker = Worker.start(config(freePort()));

        Thread.sleep(BEAT.multipliedBy(3));
        assertEquals(List.of(), registered().getList(MongoFields.ROLES_ACTIVE, String.class));
        assertEquals(2, registered().getInteger(MongoFields.ASSIGNMENT));
    }

    private TarantinoConfig config(int port) throws IOException {
        int hazelcastPort = freePort();
        return TarantinoConfig.from(Map.ofEntries(
                Map.entry("TARANTINO_PORT", String.valueOf(port)),
                Map.entry("TARANTINO_MONGO_URI", mongo.uri()),
                Map.entry("TARANTINO_HAZELCAST", LOOPBACK + ":" + hazelcastPort),
                Map.entry("TARANTINO_HAZELCAST_PORT", String.valueOf(hazelcastPort)),
                Map.entry("TARANTINO_HAZELCAST_CLUSTER", "test-" + UUID.randomUUID()),
                Map.entry("TARANTINO_NODE", "n1"),
                Map.entry("TARANTINO_ADVERTISE_HOST", LOOPBACK),
                Map.entry("TARANTINO_ROLES", "indexer"),
                Map.entry("TARANTINO_WORKER_HEARTBEAT_SECONDS", String.valueOf(BEAT.toSeconds())),
                Map.entry("TARANTINO_DATALAKE", datalake.toString()),
                Map.entry("TARANTINO_WORKLOAD", Path.of("..", "workload").toString())), TarantinoService.WORKER);
    }

    private Document registered() {
        return workers().find().first();
    }

    private MongoCollection<Document> workers() {
        return MongoDatabases.database(mongo.uri()).getCollection(MongoCollections.WORKERS);
    }

    private static boolean within(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(DEADLINE);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(Duration.ofMillis(200));
        }
        return condition.getAsBoolean();
    }

    private static int health(int port) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://" + LOOPBACK + ":" + port + "/health")).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}

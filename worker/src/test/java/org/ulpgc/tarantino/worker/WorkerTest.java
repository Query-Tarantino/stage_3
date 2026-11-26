package org.ulpgc.tarantino.worker;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerTest {

    private static final String LOOPBACK = "127.0.0.1";
    private static final Duration DEADLINE = Duration.ofSeconds(20);

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
        int hazelcastPort = freePort();
        worker = Worker.start(TarantinoConfig.from(Map.of(
                "TARANTINO_PORT", String.valueOf(port),
                "TARANTINO_MONGO_URI", mongo.uri(),
                "TARANTINO_HAZELCAST", LOOPBACK + ":" + hazelcastPort,
                "TARANTINO_HAZELCAST_PORT", String.valueOf(hazelcastPort),
                "TARANTINO_HAZELCAST_CLUSTER", "test-" + UUID.randomUUID(),
                "TARANTINO_NODE", "n1",
                "TARANTINO_ADVERTISE_HOST", LOOPBACK,
                "TARANTINO_ROLES", "indexer",
                "TARANTINO_DATALAKE", datalake.toString(),
                "TARANTINO_WORKLOAD", Path.of("..", "workload").toString()), TarantinoService.WORKER));

        assertEquals(200, health(port));
        assertEquals(List.of(new WorkerAddress("n1-w1", "n1", LOOPBACK, port)),
                new WorkerRegistry(MongoDatabases.database(mongo.uri())).alive(Duration.ofSeconds(10)));
        assertTrue(reportsActive(List.of("indexer")));
    }

    private boolean reportsActive(List<String> roles) throws InterruptedException {
        Instant deadline = Instant.now().plus(DEADLINE);
        while (Instant.now().isBefore(deadline)) {
            Document registered = MongoDatabases.database(mongo.uri()).getCollection(MongoCollections.WORKERS).find().first();
            if (registered != null && roles.equals(registered.getList(MongoFields.ROLES_ACTIVE, String.class))) {
                return true;
            }
            Thread.sleep(Duration.ofMillis(200));
        }
        return false;
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

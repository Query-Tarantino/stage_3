package org.ulpgc.tarantino.common.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.Assignment;
import org.ulpgc.tarantino.common.model.WorkerAddress;
import org.ulpgc.tarantino.common.model.WorkerIdentity;
import org.ulpgc.tarantino.common.model.WorkerRegistration;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkerRegistryTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final List<String> EVERY_ROLE = List.of("crawler", "indexer", "query");

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void twoRegistrationsOfOneWorkerAtOnceLeaveOneDocumentWithItsFirstAssignment() throws Exception {
        WorkerRegistry registry = registry();

        TwoAtOnce.call(() -> {
            registry.register(registration("n1", EVERY_ROLE));
            return null;
        });

        assertEquals(1, workers().countDocuments());
        Document worker = workers().find().first();
        assertNotNull(worker);
        assertEquals("n1", worker.getString(MongoFields.NODE));
        assertEquals("10.0.0.11", worker.getString(MongoFields.HOST));
        assertEquals(7000, worker.getInteger(MongoFields.PORT));
        assertEquals(EVERY_ROLE, worker.getList(MongoFields.ROLES_ASSIGNED, String.class));
        assertEquals(List.of(), worker.getList(MongoFields.ROLES_ACTIVE, String.class));
        assertEquals(1, worker.getInteger(MongoFields.ASSIGNMENT));
        assertEquals(Map.of("http", 250, "index", 1), worker.get(MongoFields.THREADS, Document.class));
    }

    @Test
    void aWorkerThatRegistersAgainKeepsTheRolesAssignedSince() {
        WorkerRegistry registry = registry();
        registry.register(registration("n1", EVERY_ROLE));
        workers().updateOne(Filters.eq(MongoFields.ID, "n1-w1"),
                Updates.combine(Updates.set(MongoFields.ROLES_ASSIGNED, List.of("query")), Updates.set(MongoFields.ASSIGNMENT, 3)));

        registry.register(registration("n1", EVERY_ROLE));

        assertEquals(Optional.of(new Assignment(List.of("query"), 3)), registry.beat("n1-w1", List.of()));
    }

    @Test
    void aBeatReportsTheActiveRolesAndReturnsTheAssignmentOfThatMoment() throws Exception {
        WorkerRegistry registry = registry();
        registry.register(registration("n1", List.of("indexer")));

        List<Optional<Assignment>> beats = TwoAtOnce.call(() -> registry.beat("n1-w1", List.of("indexer")));

        beats.forEach(beat -> assertEquals(Optional.of(new Assignment(List.of("indexer"), 1)), beat));
        assertEquals(List.of("indexer"), workers().find().first().getList(MongoFields.ROLES_ACTIVE, String.class));
        assertEquals(Optional.empty(), registry.beat("n9-w1", List.of()));
    }

    @Test
    void listsOnlyTheWorkersThatBeatWithinTheTimeout() {
        WorkerRegistry registry = registry();
        registry.register(registration("n2", EVERY_ROLE));
        registry.register(registration("n1", EVERY_ROLE));
        registry.register(registration("n3", EVERY_ROLE));
        workers().updateOne(Filters.eq(MongoFields.ID, "n3-w1"),
                Updates.set(MongoFields.HEARTBEAT_AT, Date.from(Instant.now().minus(TIMEOUT.multipliedBy(2)))));

        assertEquals(List.of(new WorkerAddress("n1-w1", "n1", "10.0.0.11", 7000), new WorkerAddress("n2-w1", "n2", "10.0.0.11", 7000)),
                registry.alive(TIMEOUT));
    }

    private static WorkerRegistration registration(String node, List<String> roles) {
        return new WorkerRegistration(new WorkerIdentity(node + "-w1", node), "10.0.0.11", 7000, "local",
                Map.of("http", 250, "index", 1), "3f2a9c1", roles);
    }

    private WorkerRegistry registry() {
        return new WorkerRegistry(database());
    }

    private MongoCollection<Document> workers() {
        return database().getCollection(MongoCollections.WORKERS);
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}

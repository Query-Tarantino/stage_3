package org.ulpgc.tarantino.common.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.ulpgc.tarantino.common.model.Assignment;
import org.ulpgc.tarantino.common.model.WorkerAddress;
import org.ulpgc.tarantino.common.model.WorkerRegistration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ASSIGNED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ASSIGNMENT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.COMMIT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.DESIGN;
import static org.ulpgc.tarantino.common.mongo.MongoFields.HEARTBEAT_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.HOST;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.NODE;
import static org.ulpgc.tarantino.common.mongo.MongoFields.PORT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ROLES_ACTIVE;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ROLES_ASSIGNED;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STARTED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.THREADS;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.ifNull;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.literal;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

/**
 * The registry of workers in {@code workers} (SPEC §4.3): “Register a worker” and “Beat as a worker” (SPEC §5.1), and
 * the workers alive by the server's clock.
 */
public class WorkerRegistry {

    private static final int FIRST_ASSIGNMENT = 1;

    private final MongoCollection<Document> workers;

    public WorkerRegistry(MongoDatabase database) {
        this.workers = database.getCollection(MongoCollections.WORKERS);
    }

    /** “Register a worker”: a worker that registered before keeps the roles it was assigned since (SPEC §13.2). */
    public void register(WorkerRegistration registration) {
        Document change = new Document(NODE, registration.identity().node())
                .append(HOST, registration.host())
                .append(PORT, registration.port())
                .append(DESIGN, registration.design())
                .append(THREADS, literal(new Document(registration.threads())))
                .append(COMMIT, registration.commit())
                .append(STARTED_AT, NOW)
                .append(HEARTBEAT_AT, NOW)
                .append(ROLES_ACTIVE, literal(List.of()))
                .append(ROLES_ASSIGNED, ifNull(ROLES_ASSIGNED, literal(registration.roles())))
                .append(ASSIGNMENT, ifNull(ASSIGNMENT, FIRST_ASSIGNMENT))
                .append(ASSIGNED_AT, ifNull(ASSIGNED_AT, NOW));
        workers.updateOne(Filters.eq(ID, registration.identity().workerId()), set(change), new UpdateOptions().upsert(true));
    }

    /** “Beat as a worker”: the assignment of this moment, or empty if the worker has no document and must register again. */
    public Optional<Assignment> beat(String workerId, List<String> activeRoles) {
        Document change = new Document(HEARTBEAT_AT, NOW).append(ROLES_ACTIVE, literal(activeRoles));
        return Optional.ofNullable(workers.findOneAndUpdate(Filters.eq(ID, workerId), set(change),
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)))
                .map(worker -> new Assignment(worker.getList(ROLES_ASSIGNED, String.class), worker.getInteger(ASSIGNMENT)));
    }

    /** The workers whose {@code heartbeat_at} is newer than {@code $$NOW} minus {@code timeout}, ordered by {@code worker_id}. */
    public List<WorkerAddress> alive(Duration timeout) {
        Document newerThanTimeout = new Document("$gt", List.of("$" + HEARTBEAT_AT,
                new Document("$subtract", List.of(NOW, timeout.toMillis()))));
        return workers.find(Filters.expr(newerThanTimeout))
                .sort(new Document(ID, 1))
                .map(worker -> new WorkerAddress(worker.getString(ID), worker.getString(NODE), worker.getString(HOST), worker.getInteger(PORT)))
                .into(new ArrayList<>());
    }
}

package org.ulpgc.tarantino.indexer;

import io.javalin.Javalin;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.WorkerRegistry;
import org.ulpgc.tarantino.common.operations.CrashPoints;
import org.ulpgc.tarantino.common.operations.LeaseHeartbeat;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.startup.WorkerThreads;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.adapters.broker.BrokerIndexQueue;
import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.documents.HttpPeerDocuments;
import org.ulpgc.tarantino.indexer.adapters.documents.NodeDocumentReader;
import org.ulpgc.tarantino.indexer.adapters.http.BookRoutes;
import org.ulpgc.tarantino.indexer.adapters.http.IndexRoutes;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoBatchStore;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoIndexStatus;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoIndexWriter;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoRebuilds;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoStoredDocuments;
import org.ulpgc.tarantino.indexer.commands.BatchCollector;
import org.ulpgc.tarantino.indexer.commands.BookReader;
import org.ulpgc.tarantino.indexer.commands.IndexBatchCommand;
import org.ulpgc.tarantino.indexer.commands.IndexWorker;
import org.ulpgc.tarantino.indexer.commands.IndexerRole;
import org.ulpgc.tarantino.indexer.ports.BooksToIndex;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.time.Clock;
import java.util.function.Consumer;

/** Builds the index worker and the HTTP routes of {@code indexer} over its connections (SPEC §2.2). */
public final class IndexerFactory {

    private static final String BATCH_LOOP_THREAD = "index-batches";

    private IndexerFactory() {
    }

    /**
     * The role {@code indexer}: its batch command is built once, reading {@code stopwords.txt} when the worker starts, and
     * each start gives a new batch loop, with its own handle on {@code books-to-index}, on the thread
     * {@value #BATCH_LOOP_THREAD} (SPEC §2.4 and §13.3).
     */
    public static IndexerRole role(TarantinoConfig config, Connections connections) {
        IndexBatchCommand index = indexCommand(config, connections);
        return new IndexerRole(() -> indexWorker(config, connections.broker(), index), loop -> WorkerThreads.start(BATCH_LOOP_THREAD, loop));
    }

    private static IndexWorker indexWorker(TarantinoConfig config, Broker broker, IndexBatchCommand index) {
        BatchCollector collector = new BatchCollector(new BrokerIndexQueue(broker.queue(MessagingContracts.BOOKS_TO_INDEX)),
                config.indexBatch(), config.batchTimeout(), Clock.systemUTC());
        return new IndexWorker(collector, index::execute);
    }

    /** The batch command, whose batches are owned by this worker and announced on {@code published-batches} (SPEC §4.2 and §5.1). */
    private static IndexBatchCommand indexCommand(TarantinoConfig config, Connections connections) {
        MongoBatchStore batches = new MongoBatchStore(connections.database(), config.lease(), config.workerId());
        return new IndexBatchCommand(batches, new BookReader(documentReader(config, connections), tokenizer(config)),
                new MongoIndexWriter(connections.database()), connections.broker().announcements()::publish, new TransitionLog(),
                new CrashPoints(config.crashAt()), renewal -> LeaseHeartbeat.start(config.heartbeat(), renewal));
    }

    public static Consumer<Javalin> routes(Connections connections) {
        IndexRoutes index = new IndexRoutes(new MongoRebuilds(connections.database()), new MongoIndexStatus(connections.database()));
        BookRoutes books = new BookRoutes(new BookStates(connections.database())::state, booksToIndex(connections), Clock.systemUTC());
        return app -> {
            index.register(app);
            books.register(app);
        };
    }

    private static BooksToIndex booksToIndex(Connections connections) {
        Broker broker = connections.broker();
        return task -> broker.offer(MessagingContracts.BOOKS_TO_INDEX, task);
    }

    /** Each book from this node or from another alive one that holds it (SPEC §4.6). */
    private static DatalakeReader documentReader(TarantinoConfig config, Connections connections) {
        WorkerRegistry registry = new WorkerRegistry(connections.database());
        return new NodeDocumentReader(config.node(), new BatchBasedDatalakeReader(config.datalake()),
                new MongoStoredDocuments(connections.database()), () -> registry.alive(config.workerTimeout()),
                new HttpPeerDocuments(config.peerTimeout()));
    }

    private static Tokenizer tokenizer(TarantinoConfig config) {
        return new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords());
    }
}

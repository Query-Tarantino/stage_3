package org.ulpgc.tarantino.indexer;

import io.javalin.Javalin;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.operations.CrashPoints;
import org.ulpgc.tarantino.common.operations.LeaseHeartbeat;
import org.ulpgc.tarantino.common.operations.Replica;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.adapters.broker.BrokerIndexQueue;
import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.http.BookRoutes;
import org.ulpgc.tarantino.indexer.adapters.http.IndexRoutes;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoBatchStore;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoIndexStatus;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoIndexWriter;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoRebuilds;
import org.ulpgc.tarantino.indexer.commands.BatchCollector;
import org.ulpgc.tarantino.indexer.commands.BookReader;
import org.ulpgc.tarantino.indexer.commands.IndexBatchCommand;
import org.ulpgc.tarantino.indexer.commands.IndexWorker;
import org.ulpgc.tarantino.indexer.ports.BatchAnnouncer;
import org.ulpgc.tarantino.indexer.ports.BooksToIndex;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.time.Clock;
import java.util.function.Consumer;

/** Builds the index worker and the HTTP routes of {@code indexer} over its connections (SPEC §2.2). */
public final class IndexerFactory {

    private IndexerFactory() {
    }

    /** The batch loop over {@code books-to-index}, which announces each published batch on {@code published-batches} (SPEC §4.2). */
    public static Runnable indexWorker(TarantinoConfig config, Connections connections) {
        Broker broker = connections.broker();
        MongoBatchStore batches = new MongoBatchStore(connections.database(), config.lease(), Replica.name());
        BatchCollector collector = new BatchCollector(new BrokerIndexQueue(broker.queue(MessagingContracts.BOOKS_TO_INDEX)),
                config.indexBatch(), config.batchTimeout(), Clock.systemUTC());
        return new IndexWorker(collector, indexCommand(config, connections, batches, broker.announcements()::publish));
    }

    private static IndexBatchCommand indexCommand(TarantinoConfig config, Connections connections, MongoBatchStore batches,
                                                  BatchAnnouncer announcer) {
        return new IndexBatchCommand(batches, new BookReader(datalakeReader(config), tokenizer(config)),
                new MongoIndexWriter(connections.database()), announcer, new TransitionLog(), new CrashPoints(config.crashAt()),
                renewal -> LeaseHeartbeat.start(config.heartbeat(), renewal));
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

    public static DatalakeReader datalakeReader(TarantinoConfig config) {
        return new BatchBasedDatalakeReader(config.datalake());
    }

    private static Tokenizer tokenizer(TarantinoConfig config) {
        return new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords());
    }
}

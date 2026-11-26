package org.ulpgc.tarantino.indexer.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The batch loop of the role {@code indexer}. If Mongo or Hazelcast fail halfway, it leaves the batch unpublished and
 * goes on: its lease expires and {@code control} sends its books back to the queue. Once asked to stop, it finishes the
 * batch it has open, closes its queue handle and returns (SPEC §13.3).
 */
public class IndexWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexWorker.class);
    private static final Duration PAUSE_AFTER_FAILURE = Duration.ofSeconds(1);

    private final BatchCollector collector;
    private final Consumer<Map<Integer, Instant>> index;
    private volatile boolean stopping;

    /** {@code index} indexes the books of one batch, each with the {@code enqueued_at} of its message. */
    public IndexWorker(BatchCollector collector, Consumer<Map<Integer, Instant>> index) {
        this.collector = collector;
        this.index = index;
    }

    @Override
    public void run() {
        try {
            while (!stopping && !Thread.currentThread().isInterrupted()) {
                indexNextBatchOrPause();
            }
        } finally {
            closeQueueHandle();
        }
    }

    /** A handle that fails to close leaves its messages to Hazelcast, which gives them back by itself (SPEC §4.2). */
    private void closeQueueHandle() {
        try {
            collector.close();
        } catch (RuntimeException e) {
            LOGGER.warn("Could not close the queue handle of a batch loop", e);
        }
    }

    private void indexNextBatchOrPause() {
        try {
            indexNextBatch();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            LOGGER.warn("Dropped a batch", e);
            pauseAfterFailure();
        }
    }

    /** Asks the loop to return after the batch it has open. */
    public void stop() {
        stopping = true;
    }

    private void indexNextBatch() throws InterruptedException {
        Map<Integer, Instant> books = collector.next();
        if (books.isEmpty()) {
            return;
        }
        try {
            index.accept(books);
        } finally {
            collector.acknowledge();
        }
    }

    private static void pauseAfterFailure() {
        try {
            Thread.sleep(PAUSE_AFTER_FAILURE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

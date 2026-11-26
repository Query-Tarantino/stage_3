package org.ulpgc.tarantino.indexer.ports;

import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** The {@code books-to-index} queue (SPEC §4.2). */
public interface IndexQueue extends AutoCloseable {

    Optional<BookTask> next(Duration wait) throws InterruptedException;

    /** Up to {@code max} messages that are already waiting, without blocking. */
    List<BookTask> drain(int max);

    /** Acknowledges the messages taken so far, once their batch is over (SPEC §4.2). */
    default void acknowledge() {
    }

    /** Gives back what was taken and not acknowledged, at the end of the batch loop that held this handle (SPEC §4.2). */
    @Override
    default void close() {
    }
}

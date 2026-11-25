package org.ulpgc.tarantino.common.messaging;

import org.ulpgc.tarantino.common.model.BookTask;

/** The queues and the topic of SPEC §4.2 in Hazelcast. */
public interface Broker extends AutoCloseable {

    /** A new handle on the queue on each call. */
    BookTasks queue(String name);

    /**
     * Offers one task through a handle of its own, closed right after, for a thread that keeps no handle, such as an
     * HTTP handler (SPEC §4.2).
     */
    default void offer(String queue, BookTask task) {
        try (BookTasks handle = queue(queue)) {
            handle.offer(task);
        }
    }

    BatchAnnouncements announcements();

    /** The messages waiting in the queue, for step 4 of SPEC §5.3 and {@code GET /control/status}. */
    int size(String queue);

    boolean connected();

    /** What {@code /health} lists in {@code down} while the broker does not answer (SPEC §6.5). */
    String name();

    @Override
    void close();
}

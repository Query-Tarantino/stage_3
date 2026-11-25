package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** The books waiting in {@code book_states} for a queue, oldest first, for step 4 of the reconciliation (SPEC §5.3). */
public interface WaitingBooks {

    /** The state of each of these books that has a document. */
    Map<Integer, BookStatus> statuses(Collection<Integer> bookIds);

    /** Up to {@code limit} books in {@code status} outside {@code excluded}, by {@code enqueued_at} and {@code _id}. */
    List<BookTask> oldest(BookStatus status, Collection<Integer> excluded, int limit);
}

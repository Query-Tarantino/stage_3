package org.ulpgc.tarantino.common.messaging;

/** The names of the queues and the topic of SPEC §4.2. */
public final class MessagingContracts {

    public static final String BOOKS_TO_DOWNLOAD = "books-to-download";
    public static final String BOOKS_TO_INDEX = "books-to-index";
    public static final String PUBLISHED_BATCHES = "published-batches";

    private MessagingContracts() {
    }
}

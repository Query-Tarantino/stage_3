package org.ulpgc.tarantino.crawler.model;

import java.time.Instant;

/** A book this worker claimed with “Claim a book” (SPEC §5.1), with the {@code enqueued_at} of its message. */
public record ClaimedBook(int bookId, String claimId, Instant enqueuedAt) {
}

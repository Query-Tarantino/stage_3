package org.ulpgc.tarantino.indexer.ports.sources;

import org.ulpgc.tarantino.indexer.model.book.BookText;

/** What one node answered when asked for a book: a copy, not having it, or no answer in time (SPEC §4.6). */
public sealed interface DocumentAnswer {

    record Copy(BookText text) implements DocumentAnswer {
    }

    record NotHere() implements DocumentAnswer {
    }

    record NoAnswer() implements DocumentAnswer {
    }
}

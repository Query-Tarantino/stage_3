package org.ulpgc.tarantino.indexer.ports.sources;

import org.ulpgc.tarantino.indexer.model.book.BookText;

/**
 * What one node answered when asked for a book (SPEC §4.6): a copy, not having it, an answer that says neither for this
 * book, or no answer at all because the node could not be reached.
 */
public sealed interface DocumentAnswer {

    record Copy(BookText text) implements DocumentAnswer {
    }

    record NotHere() implements DocumentAnswer {
    }

    record NoAnswer() implements DocumentAnswer {
    }

    record Unreachable() implements DocumentAnswer {
    }
}

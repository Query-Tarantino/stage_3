package org.ulpgc.tarantino.indexer.model.book;

/**
 * What looking for a book's text gave (SPEC §4.6): a copy with the right digest; no copy now, with some node of its
 * {@code locations} not alive or not answering; or no copy anywhere, with every node of its {@code locations} alive.
 */
public sealed interface BookLookup {

    record Found(BookText text) implements BookLookup {
    }

    record Unavailable() implements BookLookup {
    }

    record Lost() implements BookLookup {
    }
}

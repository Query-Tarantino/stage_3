package org.ulpgc.tarantino.indexer.ports.sources;

import org.ulpgc.tarantino.indexer.model.book.BookLookup;

/** The documents as {@code indexer} reads them, from wherever a copy is, which it never writes (SPEC §4.6). */
public interface DatalakeReader {

    BookLookup lookup(int bookId);
}

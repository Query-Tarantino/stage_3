package org.ulpgc.tarantino.indexer.adapters.documents;

import java.util.Optional;

/** The {@code locations} and {@code sha256} of a book in {@code book_states}; empty if the book has no state. */
public interface StoredDocuments {

    Optional<StoredDocument> stored(int bookId);
}

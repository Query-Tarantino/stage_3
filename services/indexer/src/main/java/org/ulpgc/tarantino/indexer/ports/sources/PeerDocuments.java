package org.ulpgc.tarantino.indexer.ports.sources;

import org.ulpgc.tarantino.common.model.WorkerAddress;

/** {@code GET /internal/documents/{book_id}} to another worker (SPEC §6.8). */
public interface PeerDocuments {

    DocumentAnswer fetch(WorkerAddress worker, int bookId);
}

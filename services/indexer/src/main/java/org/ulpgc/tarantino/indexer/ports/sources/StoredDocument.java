package org.ulpgc.tarantino.indexer.ports.sources;

import java.util.List;

/** Where a book's copies are, the downloader's node first, and the digest every copy must have (SPEC §4.3 and §4.6). */
public record StoredDocument(List<String> locations, String sha256) {
}

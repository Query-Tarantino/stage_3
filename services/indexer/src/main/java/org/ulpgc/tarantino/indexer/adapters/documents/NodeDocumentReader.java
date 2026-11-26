package org.ulpgc.tarantino.indexer.adapters.documents;

import org.ulpgc.tarantino.common.datalake.DocumentDigest;
import org.ulpgc.tarantino.common.model.WorkerAddress;
import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.model.book.BookLookup;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads a book from wherever a copy is (SPEC §4.6): from this node's files when this node is in its {@code locations},
 * and otherwise from an alive worker of each other node of {@code locations}, in their order. Only a copy whose digest
 * is the book's {@code sha256} counts. With no such copy, the book is lost only if every node of {@code locations} is
 * alive and answered, and unavailable otherwise. It keeps nothing it fetched.
 */
public class NodeDocumentReader implements DatalakeReader {

    private final String node;
    private final BatchBasedDatalakeReader files;
    private final StoredDocuments documents;
    private final Supplier<List<WorkerAddress>> aliveWorkers;
    private final PeerDocuments peers;

    public NodeDocumentReader(String node, BatchBasedDatalakeReader files, StoredDocuments documents,
                              Supplier<List<WorkerAddress>> aliveWorkers, PeerDocuments peers) {
        this.node = node;
        this.files = files;
        this.documents = documents;
        this.aliveWorkers = aliveWorkers;
        this.peers = peers;
    }

    @Override
    public BookLookup lookup(int bookId) {
        Optional<StoredDocument> stored = documents.stored(bookId);
        if (stored.isEmpty()) {
            return new BookLookup.Lost();
        }
        Map<String, List<WorkerAddress>> aliveByNode = aliveWorkers.get().stream()
                .collect(Collectors.groupingBy(WorkerAddress::node));
        boolean everyNodeAnswered = true;
        for (String location : readingOrder(stored.get().locations())) {
            DocumentAnswer answer = ask(location, aliveByNode.getOrDefault(location, List.of()), bookId);
            if (answer instanceof DocumentAnswer.Copy(BookText text) && hasDigest(text, stored.get().sha256())) {
                return new BookLookup.Found(text);
            }
            everyNodeAnswered &= !(answer instanceof DocumentAnswer.NoAnswer);
        }
        return everyNodeAnswered ? new BookLookup.Lost() : new BookLookup.Unavailable();
    }

    private List<String> readingOrder(List<String> locations) {
        Stream<String> here = locations.contains(node) ? Stream.of(node) : Stream.empty();
        return Stream.concat(here, locations.stream().filter(location -> !location.equals(node))).toList();
    }

    private DocumentAnswer ask(String location, List<WorkerAddress> workers, int bookId) {
        if (location.equals(node)) {
            return files.bookText(bookId).<DocumentAnswer>map(DocumentAnswer.Copy::new).orElseGet(DocumentAnswer.NotHere::new);
        }
        return workers.stream()
                .map(worker -> peers.fetch(worker, bookId))
                .filter(answer -> !(answer instanceof DocumentAnswer.NoAnswer))
                .findFirst()
                .orElseGet(DocumentAnswer.NoAnswer::new);
    }

    private static boolean hasDigest(BookText text, String sha256) {
        return DocumentDigest.of(text.header(), text.body()).equals(sha256);
    }
}

package org.ulpgc.tarantino.indexer.adapters.documents;

import org.ulpgc.tarantino.common.datalake.DocumentDigest;
import org.ulpgc.tarantino.common.model.WorkerAddress;
import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.model.book.BookLookup;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;
import org.ulpgc.tarantino.indexer.ports.sources.DocumentAnswer;
import org.ulpgc.tarantino.indexer.ports.sources.PeerDocuments;
import org.ulpgc.tarantino.indexer.ports.sources.StoredDocument;
import org.ulpgc.tarantino.indexer.ports.sources.StoredDocuments;

import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads a book from wherever a copy is (SPEC §4.6): from this node's files when this node is in its {@code locations},
 * and otherwise from an alive worker of each other node of {@code locations}, in their order. Only a copy whose digest
 * is the book's {@code sha256} counts. With no such copy, the book is lost only if every node of {@code locations} is
 * alive and answered, and unavailable otherwise. A node that gave no answer is not asked again for
 * {@code TARANTINO_WORKER_TIMEOUT_SECONDS}, so a frozen node costs one timeout and not one per book. It keeps nothing it
 * fetched.
 */
public class NodeDocumentReader implements DatalakeReader {

    private final String node;
    private final BatchBasedDatalakeReader files;
    private final StoredDocuments documents;
    private final Supplier<List<WorkerAddress>> aliveWorkers;
    private final PeerDocuments peers;
    private final Clock clock;
    private final Duration silence;
    private final Map<String, Instant> silentUntil = new ConcurrentHashMap<>();

    /** {@code silence} is how long a node that gave no answer counts as not answering without being asked. */
    public NodeDocumentReader(String node, BatchBasedDatalakeReader files, StoredDocuments documents,
                              Supplier<List<WorkerAddress>> aliveWorkers, PeerDocuments peers, Clock clock, Duration silence) {
        this.node = node;
        this.files = files;
        this.documents = documents;
        this.aliveWorkers = aliveWorkers;
        this.peers = peers;
        this.clock = clock;
        this.silence = silence;
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
            return readHere(bookId);
        }
        if (silentUntil.getOrDefault(location, Instant.MIN).isAfter(clock.instant())) {
            return new DocumentAnswer.NoAnswer();
        }
        DocumentAnswer answer = askAnAliveWorker(workers, bookId);
        if (answer instanceof DocumentAnswer.NoAnswer && !workers.isEmpty()) {
            silentUntil.put(location, clock.instant().plus(silence));
        }
        return answer;
    }

    /** A copy here that cannot be read counts as no answer, so it never makes the book lost. */
    private DocumentAnswer readHere(int bookId) {
        try {
            return files.bookText(bookId).<DocumentAnswer>map(DocumentAnswer.Copy::new).orElseGet(DocumentAnswer.NotHere::new);
        } catch (UncheckedIOException e) {
            return new DocumentAnswer.NoAnswer();
        }
    }

    private DocumentAnswer askAnAliveWorker(List<WorkerAddress> workers, int bookId) {
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

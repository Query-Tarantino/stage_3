package org.ulpgc.tarantino.indexer.adapters.documents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.datalake.DatalakePaths;
import org.ulpgc.tarantino.common.datalake.DocumentDigest;
import org.ulpgc.tarantino.common.model.WorkerAddress;
import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.model.book.BookLookup;
import org.ulpgc.tarantino.indexer.model.book.BookText;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeDocumentReaderTest {

    private static final int BOOK = 1342;
    private static final BookText TEXT = new BookText(BOOK, "Title: Pride and Prejudice\n", "It is a truth universally acknowledged");
    private static final BookText TAMPERED = new BookText(BOOK, TEXT.header(), TEXT.body() + ".");
    private static final String SHA256 = DocumentDigest.of(TEXT.header(), TEXT.body());

    @TempDir
    Path datalake;

    private final Map<Integer, StoredDocument> stored = new HashMap<>();
    private final List<WorkerAddress> alive = new ArrayList<>(List.of(worker("n1"), worker("n2"), worker("n3"), worker("n4")));
    private final Map<String, DocumentAnswer> answers = new HashMap<>();
    private final List<String> asked = new ArrayList<>();

    @Test
    void readsHereWhenThisNodeHoldsAGoodCopy() throws IOException {
        locatedOn("n1", "n2");
        here(TEXT);

        assertEquals(new BookLookup.Found(TEXT), reader().lookup(BOOK));
        assertEquals(List.of(), asked);
    }

    @Test
    void readsFromAnotherNodeWhenThisOneIsNotInItsLocations() {
        locatedOn("n1", "n3");
        answers.put("n1", new DocumentAnswer.Copy(TEXT));

        assertEquals(new BookLookup.Found(TEXT), reader().lookup(BOOK));
        assertEquals(List.of("n1"), asked);
    }

    @Test
    void goesOnToTheNextNodeWhenACopyHasAnotherDigest() throws IOException {
        locatedOn("n2", "n1", "n3");
        here(TAMPERED);
        answers.put("n1", new DocumentAnswer.Copy(TAMPERED));
        answers.put("n3", new DocumentAnswer.Copy(TEXT));

        assertEquals(new BookLookup.Found(TEXT), reader().lookup(BOOK));
        assertEquals(List.of("n1", "n3"), asked);
    }

    @Test
    void isUnavailableWhenANodeOfItsLocationsIsNotAlive() {
        locatedOn("n1", "n3");
        alive.removeIf(worker -> worker.node().equals("n3"));
        answers.put("n1", new DocumentAnswer.NotHere());

        assertEquals(new BookLookup.Unavailable(), reader().lookup(BOOK));
    }

    @Test
    void isUnavailableWhenAnAliveNodeDoesNotAnswer() {
        locatedOn("n1", "n3");
        answers.put("n1", new DocumentAnswer.Copy(TAMPERED));
        answers.put("n3", new DocumentAnswer.NoAnswer());

        assertEquals(new BookLookup.Unavailable(), reader().lookup(BOOK));
    }

    @Test
    void isLostWhenEveryNodeOfItsLocationsIsAliveAndHasNoGoodCopy() throws IOException {
        locatedOn("n2", "n1", "n3");
        here(TAMPERED);
        answers.put("n1", new DocumentAnswer.NotHere());
        answers.put("n3", new DocumentAnswer.Copy(TAMPERED));

        assertEquals(new BookLookup.Lost(), reader().lookup(BOOK));
    }

    @Test
    void isLostWhenTheBookHasNoState() {
        assertEquals(new BookLookup.Lost(), reader().lookup(BOOK));
    }

    private NodeDocumentReader reader() {
        PeerDocuments peers = (worker, bookId) -> {
            asked.add(worker.node());
            return answers.getOrDefault(worker.node(), new DocumentAnswer.NotHere());
        };
        return new NodeDocumentReader("n2", new BatchBasedDatalakeReader(datalake), bookId -> Optional.ofNullable(stored.get(bookId)),
                () -> List.copyOf(alive), peers);
    }

    private void locatedOn(String... nodes) {
        stored.put(BOOK, new StoredDocument(List.of(nodes), SHA256));
    }

    private void here(BookText text) throws IOException {
        DatalakePaths paths = new DatalakePaths(datalake);
        DatalakePaths.writeAtomically(paths.header(BOOK), text.header());
        DatalakePaths.writeAtomically(paths.body(BOOK), text.body());
    }

    private static WorkerAddress worker(String node) {
        return new WorkerAddress(node + "-w1", node, "10.0.0." + node.substring(1), 7000);
    }
}

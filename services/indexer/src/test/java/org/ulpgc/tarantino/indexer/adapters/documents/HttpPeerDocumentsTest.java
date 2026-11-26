package org.ulpgc.tarantino.indexer.adapters.documents;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.WorkerAddress;
import org.ulpgc.tarantino.indexer.model.book.BookText;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpPeerDocumentsTest {

    private static final String LOOPBACK = "127.0.0.1";
    private static final Duration TIMEOUT = Duration.ofMillis(500);

    private Javalin peer;
    private WorkerAddress worker;

    @BeforeEach
    void startPeer() {
        peer = Javalin.create().get("/internal/documents/{book_id}", context -> {
            switch (context.pathParam("book_id")) {
                case "1342" -> context.json(Map.of("book_id", 1342, "header", "Title: Pride and Prejudice\n",
                        "body", "It is a truth", "sha256", "not checked here"));
                case "84" -> context.status(404);
                case "11" -> Thread.sleep(TIMEOUT.multipliedBy(4));
                default -> context.status(500);
            }
        }).start(LOOPBACK, 0);
        worker = new WorkerAddress("n1-w1", "n1", LOOPBACK, peer.port());
    }

    @AfterEach
    void stopPeer() {
        peer.stop();
    }

    @Test
    void takesTheHeaderAndBodyOfACopy() {
        assertEquals(new DocumentAnswer.Copy(new BookText(1342, "Title: Pride and Prejudice\n", "It is a truth")), peers().fetch(worker, 1342));
    }

    @Test
    void readsANotFoundAsANodeThatDoesNotHoldTheBook() {
        assertEquals(new DocumentAnswer.NotHere(), peers().fetch(worker, 84));
    }

    @Test
    void readsAnErrorATimeoutOrAClosedPortAsNoAnswer() throws IOException {
        assertEquals(new DocumentAnswer.NoAnswer(), peers().fetch(worker, 7));
        assertEquals(new DocumentAnswer.NoAnswer(), peers().fetch(worker, 11));
        assertEquals(new DocumentAnswer.NoAnswer(), peers().fetch(new WorkerAddress("n9-w1", "n9", LOOPBACK, closedPort()), 1342));
    }

    private static HttpPeerDocuments peers() {
        return new HttpPeerDocuments(TIMEOUT);
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}

package org.ulpgc.tarantino.indexer.adapters.documents;

import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.model.WorkerAddress;
import org.ulpgc.tarantino.indexer.model.book.BookText;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Asks another worker for a book with {@code GET /internal/documents/{book_id}} (SPEC §6.8), waiting at most
 * {@code TARANTINO_PEER_TIMEOUT_SECONDS}: {@code 200} is a copy, {@code 404} means that node does not hold it, and
 * anything else, a timeout included, is no answer.
 */
public class HttpPeerDocuments implements PeerDocuments {

    private static final String DOCUMENTS_PATH = "/internal/documents/";
    private static final int OK = 200;
    private static final int NOT_FOUND = 404;

    private final HttpClient client;
    private final Duration timeout;

    public HttpPeerDocuments(Duration timeout) {
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.timeout = timeout;
    }

    @Override
    public DocumentAnswer fetch(WorkerAddress worker, int bookId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + worker.host() + ":" + worker.port() + DOCUMENTS_PATH + bookId))
                .timeout(timeout)
                .GET()
                .build();
        try {
            return answer(client.send(request, HttpResponse.BodyHandlers.ofString()), bookId);
        } catch (IOException | RuntimeException e) {
            return new DocumentAnswer.NoAnswer();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DocumentAnswer.NoAnswer();
        }
    }

    private static DocumentAnswer answer(HttpResponse<String> response, int bookId) {
        return switch (response.statusCode()) {
            case OK -> {
                PeerCopy copy = TarantinoJson.read(response.body(), PeerCopy.class);
                yield new DocumentAnswer.Copy(new BookText(bookId, copy.header(), copy.body()));
            }
            case NOT_FOUND -> new DocumentAnswer.NotHere();
            default -> new DocumentAnswer.NoAnswer();
        };
    }

    private record PeerCopy(String header, String body) {
    }
}

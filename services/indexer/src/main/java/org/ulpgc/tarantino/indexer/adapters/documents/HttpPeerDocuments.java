package org.ulpgc.tarantino.indexer.adapters.documents;

import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.model.ProblemCode;
import org.ulpgc.tarantino.common.model.WorkerAddress;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.ports.sources.DocumentAnswer;
import org.ulpgc.tarantino.indexer.ports.sources.PeerDocuments;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Asks another worker for a book with {@code GET /internal/documents/{book_id}} (SPEC §6.8), waiting at most
 * {@code TARANTINO_PEER_TIMEOUT_SECONDS} for the whole answer: {@code 200} is a copy, a {@code 404} with
 * {@code BOOK_NOT_FOUND} means that node does not hold it, any other answer says nothing about this book, and a refused
 * connection or a timeout means the worker could not be reached.
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
        CompletableFuture<HttpResponse<String>> response;
        try {
            response = client.sendAsync(request(worker, bookId), HttpResponse.BodyHandlers.ofString());
        } catch (RuntimeException e) {
            return new DocumentAnswer.NoAnswer();
        }
        return await(response, bookId);
    }

    private HttpRequest request(WorkerAddress worker, int bookId) {
        return HttpRequest.newBuilder(URI.create("http://" + worker.host() + ":" + worker.port() + DOCUMENTS_PATH + bookId))
                .timeout(timeout)
                .GET()
                .build();
    }

    private DocumentAnswer await(CompletableFuture<HttpResponse<String>> response, int bookId) {
        try {
            return answer(response.get(timeout.toMillis(), TimeUnit.MILLISECONDS), bookId);
        } catch (ExecutionException | TimeoutException e) {
            response.cancel(true);
            return new DocumentAnswer.Unreachable();
        } catch (RuntimeException e) {
            return new DocumentAnswer.NoAnswer();
        } catch (InterruptedException e) {
            response.cancel(true);
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
            case NOT_FOUND -> notHereOrNoAnswer(response.body());
            default -> new DocumentAnswer.NoAnswer();
        };
    }

    /** Only the route's own {@code 404} says the node lacks the book; a path the worker does not serve says nothing. */
    private static DocumentAnswer notHereOrNoAnswer(String body) {
        Problem problem = TarantinoJson.read(body, Problem.class);
        return ProblemCode.BOOK_NOT_FOUND.name().equals(problem.code()) ? new DocumentAnswer.NotHere() : new DocumentAnswer.NoAnswer();
    }

    private record PeerCopy(String header, String body) {
    }

    private record Problem(String code) {
    }
}

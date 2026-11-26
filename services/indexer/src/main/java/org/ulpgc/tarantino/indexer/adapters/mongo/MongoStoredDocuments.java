package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.indexer.ports.sources.StoredDocument;
import org.ulpgc.tarantino.indexer.ports.sources.StoredDocuments;

import java.util.List;
import java.util.Optional;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LOCATIONS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.SHA256;

/** The {@code locations} and {@code sha256} of a book in {@code book_states} (SPEC §4.3), as “Finish a download” left them. */
public class MongoStoredDocuments implements StoredDocuments {

    private final MongoCollection<Document> states;

    public MongoStoredDocuments(MongoDatabase database) {
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
    }

    @Override
    public Optional<StoredDocument> stored(int bookId) {
        return Optional.ofNullable(states.find(Filters.eq(ID, bookId)).projection(Projections.include(LOCATIONS, SHA256)).first())
                .map(state -> new StoredDocument(state.getList(LOCATIONS, String.class, List.of()), state.getString(SHA256)));
    }
}

package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.indexer.ports.sources.StoredDocument;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoStoredDocumentsTest {

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void readsTheLocationsInOrderAndTheDigestOfABook() {
        states().insertOne(new Document(MongoFields.ID, 1342).append(MongoFields.STATUS, "DOWNLOADED")
                .append(MongoFields.LOCATIONS, List.of("n3", "n1")).append(MongoFields.SHA256, "9f2c"));

        assertEquals(Optional.of(new StoredDocument(List.of("n3", "n1"), "9f2c")), documents().stored(1342));
    }

    @Test
    void readsABookWithoutLocationsAsHeldNowhereAndAMissingOneAsEmpty() {
        states().insertOne(new Document(MongoFields.ID, 84).append(MongoFields.STATUS, "QUEUED"));

        assertEquals(Optional.of(new StoredDocument(List.of(), null)), documents().stored(84));
        assertEquals(Optional.empty(), documents().stored(11));
    }

    private MongoStoredDocuments documents() {
        return new MongoStoredDocuments(MongoDatabases.database(mongo.uri()));
    }

    private MongoCollection<Document> states() {
        return MongoDatabases.database(mongo.uri()).getCollection(MongoCollections.BOOK_STATES);
    }
}

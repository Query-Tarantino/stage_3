package org.ulpgc.tarantino.indexer.adapters.datalake.batch;

import org.ulpgc.tarantino.common.datalake.DatalakePaths;
import org.ulpgc.tarantino.indexer.adapters.datalake.BookFiles;
import org.ulpgc.tarantino.indexer.model.book.BookText;

import java.nio.file.Path;
import java.util.Optional;

/** Reads the books of this node from the {@code batch} layout of its datalake (SPEC §4.6). */
public class BatchBasedDatalakeReader {

    private final DatalakePaths paths;

    public BatchBasedDatalakeReader(Path root) {
        this.paths = new DatalakePaths(root);
    }

    public Optional<BookText> bookText(int bookId) {
        return BookFiles.bookText(bookId, paths.header(bookId), paths.body(bookId));
    }
}

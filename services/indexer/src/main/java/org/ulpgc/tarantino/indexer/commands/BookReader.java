package org.ulpgc.tarantino.indexer.commands;

import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.model.book.BookLookup;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;
import org.ulpgc.tarantino.indexer.model.book.ParsedBook;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

/** Finds a book's text wherever a copy is and parses its header and body, as in Stage 1 (SPEC §4.6). */
public class BookReader {

    private final DatalakeReader datalake;
    private final HeaderParser headerParser;
    private final Tokenizer tokenizer;

    public BookReader(DatalakeReader datalake, Tokenizer tokenizer) {
        this.datalake = datalake;
        this.headerParser = new HeaderParser(tokenizer);
        this.tokenizer = tokenizer;
    }

    public BookLookup find(int bookId) {
        return datalake.lookup(bookId);
    }

    public ParsedBook parse(BookText text) {
        return new ParsedBook(headerParser.metadata(text), tokenizer.frequencies(text.body()));
    }
}

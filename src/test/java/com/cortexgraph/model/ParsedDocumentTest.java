package com.cortexgraph.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ParsedDocumentTest {

    @Test
    void testParsedDocumentAndPages() {
        ParsedPage p1 = new ParsedPage(1, "Page 1 intro");
        ParsedPage p2 = new ParsedPage(2, "Page 2 conclusion");
        ParsedDocument doc = new ParsedDocument("sample.pdf", List.of(p1, p2));

        assertEquals("sample.pdf", doc.getFilename());
        assertEquals(2, doc.getPages().size());
        assertEquals(1, doc.getPages().get(0).getPageNumber());
        assertEquals("Page 1 intro", doc.getPages().get(0).getContent());
        assertEquals("Page 1 intro\n\nPage 2 conclusion", doc.getFullText());
        assertNotNull(doc.toString());
        assertNotNull(p1.toString());
    }

    @Test
    void testTextChunkPageNumberBackwardCompatibility() {
        // Old 3-arg constructor defaults pageNumber to 1
        TextChunk oldChunk = new TextChunk(10, 0, "Hello");
        assertEquals(1, oldChunk.getPageNumber());

        // Full 4-arg constructor without ID defaults pageNumber to 1
        TextChunk chunkWithDefaultPage = new TextChunk(5, 10, 0, "Hello");
        assertEquals(1, chunkWithDefaultPage.getPageNumber());

        // Constructor preserving explicit pageNumber
        TextChunk newChunk = new TextChunk(10, 0, "Hello", 3);
        assertEquals(3, newChunk.getPageNumber());

        TextChunk fullNewChunk = new TextChunk(6, 10, 0, "Hello", 4);
        assertEquals(4, fullNewChunk.getPageNumber());
    }
}

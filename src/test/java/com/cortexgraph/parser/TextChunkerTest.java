package com.cortexgraph.parser;

import com.cortexgraph.exception.DocumentParseException;
import com.cortexgraph.model.ParsedDocument;
import com.cortexgraph.model.ParsedPage;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TextChunkerTest {
    private TextChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new TextChunker(10, 2); // 10 words per chunk, 2 words overlap
    }

    @Test
    void testShortDocumentCreatesSingleChunk() throws Exception {
        ParsedDocument doc = new ParsedDocument("short.txt", List.of(
            new ParsedPage(1, "One two three four five")
        ));
        List<TextChunk> chunks = chunker.chunk(1, doc);

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals("One two three four five", chunks.get(0).getContent());
        assertEquals(1, chunks.get(0).getPageNumber());
    }

    @Test
    void testOverlappingChunkBoundaries() throws Exception {
        // 18 words total
        String text = "w1 w2 w3 w4 w5 w6 w7 w8 w9 w10 w11 w12 w13 w14 w15 w16 w17 w18";
        ParsedDocument doc = new ParsedDocument("doc.txt", List.of(new ParsedPage(1, text)));
        List<TextChunk> chunks = chunker.chunk(1, doc);

        // Chunk 0: w1..w10 (10 words)
        // Step: 10 - 2 = 8 words -> Chunk 1: w9..w18 (10 words)
        assertEquals(2, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertTrue(chunks.get(0).getContent().startsWith("w1"));
        assertTrue(chunks.get(0).getContent().endsWith("w10"));

        assertEquals(1, chunks.get(1).getChunkIndex());
        assertTrue(chunks.get(1).getContent().startsWith("w9"));
        assertTrue(chunks.get(1).getContent().endsWith("w18"));
    }

    @Test
    void testMultiPageMetadataPreserved() throws Exception {
        ParsedDocument doc = new ParsedDocument("multi.txt", List.of(
            new ParsedPage(1, "Page one text"),
            new ParsedPage(2, "Page two text")
        ));
        List<TextChunk> chunks = chunker.chunk(1, doc);

        assertEquals(2, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals(1, chunks.get(0).getPageNumber());
        assertEquals(1, chunks.get(1).getChunkIndex());
        assertEquals(2, chunks.get(1).getPageNumber());
    }

    @Test
    void testDefaultConstructorParameters() {
        TextChunker defaultChunker = new TextChunker();
        assertEquals(250, defaultChunker.getChunkSize());
        assertEquals(40, defaultChunker.getOverlap());
    }

    @Test
    void testInvalidParametersThrowException() {
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(10, 10));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(10, -1));
    }

    @Test
    void testEmptyContentThrowsException() {
        ParsedDocument emptyDoc = new ParsedDocument("empty.txt", List.of(new ParsedPage(1, "   ")));
        assertThrows(DocumentParseException.class, () -> chunker.chunk(1, emptyDoc));
    }
}

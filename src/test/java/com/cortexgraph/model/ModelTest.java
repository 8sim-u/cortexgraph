package com.cortexgraph.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ModelTest {
    @Test
    void testDocumentRecord() {
        DocumentRecord newDoc = new DocumentRecord("manual.pdf", "/docs/manual.pdf", 2048L);
        assertEquals(0, newDoc.getId());
        assertEquals("manual.pdf", newDoc.getFilename());
        assertEquals("/docs/manual.pdf", newDoc.getFilePath());
        assertEquals(2048L, newDoc.getFileSize());
        assertNull(newDoc.getCreatedAt());

        DocumentRecord persistedDoc = new DocumentRecord(1, "manual.pdf", "/docs/manual.pdf", 2048L, "2026-11-22 12:00:00");
        assertEquals(1, persistedDoc.getId());
        assertEquals("2026-11-22 12:00:00", persistedDoc.getCreatedAt());
        assertNotNull(persistedDoc.toString());
    }

    @Test
    void testTextChunk() {
        TextChunk newChunk = new TextChunk(10, 0, "Hello cortex");
        assertEquals(0, newChunk.getId());
        assertEquals(10, newChunk.getDocumentId());
        assertEquals(0, newChunk.getChunkIndex());
        assertEquals("Hello cortex", newChunk.getContent());

        TextChunk persistedChunk = new TextChunk(5, 10, 0, "Hello cortex");
        assertEquals(5, persistedChunk.getId());
        assertNotNull(persistedChunk.toString());
    }

    @Test
    void testChatMessage() {
        ChatMessage newMsg = new ChatMessage("user", "What is RAG?");
        assertEquals(0, newMsg.getId());
        assertEquals("user", newMsg.getSender());
        assertEquals("What is RAG?", newMsg.getContent());
        assertNull(newMsg.getTimestamp());

        ChatMessage persistedMsg = new ChatMessage(42, "assistant", "Retrieval-Augmented Generation", "2026-11-22 12:01:00");
        assertEquals(42, persistedMsg.getId());
        assertEquals("2026-11-22 12:01:00", persistedMsg.getTimestamp());
        assertNotNull(persistedMsg.toString());
    }
}

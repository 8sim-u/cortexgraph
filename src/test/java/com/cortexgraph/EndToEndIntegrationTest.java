package com.cortexgraph;

import com.cortexgraph.dao.ChatDAO;
import com.cortexgraph.dao.ChunkDAO;
import com.cortexgraph.dao.DatabaseManager;
import com.cortexgraph.dao.DocumentDAO;
import com.cortexgraph.model.ChatMessage;
import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class EndToEndIntegrationTest {

    @Test
    void testCompleteDataLayerWorkflow(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("e2e_cortex.db");
        DatabaseManager.initializeDatabase(dbUrl);

        DocumentDAO docDAO = new DocumentDAO(dbUrl);
        ChunkDAO chunkDAO = new ChunkDAO(dbUrl);
        ChatDAO chatDAO = new ChatDAO(dbUrl);

        // 1. Insert document
        DocumentRecord doc = new DocumentRecord("manual.pdf", "/vault/manual.pdf", 8192L);
        int docId = docDAO.save(doc);
        assertTrue(docId > 0);

        Optional<DocumentRecord> retrievedDoc = docDAO.findById(docId);
        assertTrue(retrievedDoc.isPresent());
        assertEquals("manual.pdf", retrievedDoc.get().getFilename());

        // 2. Insert chunks for document
        TextChunk chunk0 = new TextChunk(docId, 0, "Chapter 1: Cortex Introduction");
        TextChunk chunk1 = new TextChunk(docId, 1, "Chapter 2: Vector and Graph Search");
        chunkDAO.saveAll(List.of(chunk0, chunk1));

        List<TextChunk> docChunks = chunkDAO.findByDocumentId(docId);
        assertEquals(2, docChunks.size());
        assertEquals("Chapter 1: Cortex Introduction", docChunks.get(0).getContent());
        assertEquals("Chapter 2: Vector and Graph Search", docChunks.get(1).getContent());

        // 3. Log chat interaction
        int msgId1 = chatDAO.save(new ChatMessage("user", "Explain Chapter 1"));
        int msgId2 = chatDAO.save(new ChatMessage("assistant", "Chapter 1 introduces Cortex..."));
        assertTrue(msgId1 > 0);
        assertTrue(msgId2 > msgId1);

        List<ChatMessage> history = chatDAO.findAll();
        assertEquals(2, history.size());

        // 4. Delete document and verify cascading chunk deletion
        boolean deleted = docDAO.delete(docId);
        assertTrue(deleted);
        assertTrue(docDAO.findById(docId).isEmpty());
        assertTrue(chunkDAO.findByDocumentId(docId).isEmpty(), "Chunks must be cascaded on document deletion");

        // 5. Clean up chat history
        chatDAO.clearHistory();
        assertTrue(chatDAO.findAll().isEmpty());
    }
}

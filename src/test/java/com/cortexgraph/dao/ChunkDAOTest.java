package com.cortexgraph.dao;

import com.cortexgraph.exception.VaultException;
import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkDAOTest {

    private ChunkDAO chunkDAO;
    private DocumentDAO docDAO;
    private int docId;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("chunk_test.db");
        DatabaseManager.initializeDatabase(dbUrl);
        docDAO = new DocumentDAO(dbUrl);
        chunkDAO = new ChunkDAO(dbUrl);
        docId = docDAO.save(new DocumentRecord("manual.pdf", "/docs/manual.pdf", 4096L));
    }

    @Test
    void testSaveAndFindByDocumentId() throws Exception {
        TextChunk c1 = new TextChunk(docId, 0, "First paragraph");
        TextChunk c2 = new TextChunk(docId, 1, "Second paragraph");
        chunkDAO.saveAll(List.of(c1, c2));

        List<TextChunk> chunks = chunkDAO.findByDocumentId(docId);
        assertEquals(2, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals("First paragraph", chunks.get(0).getContent());
        assertEquals(1, chunks.get(1).getChunkIndex());
        assertEquals("Second paragraph", chunks.get(1).getContent());

        boolean deleted = chunkDAO.deleteByDocumentId(docId);
        assertTrue(deleted);
        assertTrue(chunkDAO.findByDocumentId(docId).isEmpty());
    }

    @Test
    void testSaveSingleChunk() throws Exception {
        TextChunk c = new TextChunk(docId, 0, "Single chunk content");
        int chunkId = chunkDAO.save(c);
        assertTrue(chunkId > 0);

        List<TextChunk> chunks = chunkDAO.findByDocumentId(docId);
        assertEquals(1, chunks.size());
        assertEquals(chunkId, chunks.get(0).getId());
    }

    @Test
    void testForeignConstraintFailureOnOrphanChunk() {
        TextChunk orphan = new TextChunk(99999, 0, "Invalid document reference");
        assertThrows(VaultException.class, () -> chunkDAO.save(orphan));
    }
}

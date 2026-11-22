package com.cortexgraph.dao;

import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class EmbeddingDAOTest {
    private EmbeddingDAO embeddingDAO;
    private ChunkDAO chunkDAO;
    private DocumentDAO docDAO;
    private int chunkId1;
    private int chunkId2;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("embed_test.db");
        DatabaseManager.initializeDatabase(dbUrl);
        docDAO = new DocumentDAO(dbUrl);
        chunkDAO = new ChunkDAO(dbUrl);
        embeddingDAO = new EmbeddingDAO(dbUrl);

        int docId = docDAO.save(new DocumentRecord("test.txt", "/test.txt", 100L));
        chunkId1 = chunkDAO.save(new TextChunk(docId, 0, "Chunk text 1"));
        chunkId2 = chunkDAO.save(new TextChunk(docId, 1, "Chunk text 2"));
    }

    @Test
    void testSaveAndFindEmbedding() throws Exception {
        double[] vec = {0.1, 0.5, -0.2, 0.99};
        embeddingDAO.save(chunkId1, vec);

        Optional<double[]> fetched = embeddingDAO.findByChunkId(chunkId1);
        assertTrue(fetched.isPresent());
        assertArrayEquals(vec, fetched.get(), 1e-9);

        Optional<double[]> missing = embeddingDAO.findByChunkId(9999);
        assertTrue(missing.isEmpty());
    }

    @Test
    void testSaveAllAndFindAll() throws Exception {
        double[] vec1 = {1.0, 2.0, 3.0};
        double[] vec2 = {4.0, 5.0, 6.0};

        embeddingDAO.saveAll(Map.of(chunkId1, vec1, chunkId2, vec2));

        Map<Integer, double[]> all = embeddingDAO.findAll();
        assertEquals(2, all.size());
        assertArrayEquals(vec1, all.get(chunkId1), 1e-9);
        assertArrayEquals(vec2, all.get(chunkId2), 1e-9);

        boolean deleted = embeddingDAO.deleteByChunkId(chunkId1);
        assertTrue(deleted);
        assertTrue(embeddingDAO.findByChunkId(chunkId1).isEmpty());
    }
}

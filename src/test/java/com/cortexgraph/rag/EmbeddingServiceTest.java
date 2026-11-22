package com.cortexgraph.rag;

import com.cortexgraph.dao.ChunkDAO;
import com.cortexgraph.dao.DatabaseManager;
import com.cortexgraph.dao.DocumentDAO;
import com.cortexgraph.dao.EmbeddingDAO;
import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class EmbeddingServiceTest {
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(new DeterministicEmbeddingModel(64));
    }

    @Test
    void testDeterministicEmbeddingsConsistency() {
        double[] v1 = embeddingService.embedText("Knowledge graphs and retrieval augmented generation");
        double[] v2 = embeddingService.embedText("Knowledge graphs and retrieval augmented generation");
        double[] v3 = embeddingService.embedText("Unrelated baking recipe for cake");

        assertEquals(64, v1.length);
        assertArrayEquals(v1, v2, 1e-9);

        // Vector must be normalized (norm = 1.0)
        double norm = 0.0;
        for (double d : v1) norm += d * d;
        assertEquals(1.0, Math.sqrt(norm), 1e-6);

        // Similar text should have higher cosine similarity than unrelated text
        double simSelf = VectorUtils.cosineSimilarity(v1, v2);
        double simDiff = VectorUtils.cosineSimilarity(v1, v3);
        assertEquals(1.0, simSelf, 1e-6);
        assertTrue(simSelf > simDiff, "Self-similarity (" + simSelf + ") should be greater than cross-topic similarity (" + simDiff + ")");
    }

    @Test
    void testVectorCache() {
        TextChunk chunk = new TextChunk(42, 1, 0, "Cached text content");
        double[] v1 = embeddingService.embedChunk(chunk);
        assertNotNull(v1);

        double[] cached = embeddingService.getCachedEmbedding(String.valueOf(chunk.getId()));
        assertNotNull(cached);
        assertSame(v1, cached);
    }

    @Test
    void testDatabaseLoadAndPersistSync(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("sync_test.db");
        DatabaseManager.initializeDatabase(dbUrl);
        DocumentDAO docDAO = new DocumentDAO(dbUrl);
        ChunkDAO chunkDAO = new ChunkDAO(dbUrl);
        EmbeddingDAO embeddingDAO = new EmbeddingDAO(dbUrl);

        int docId = docDAO.save(new DocumentRecord("doc.txt", "/doc.txt", 50L));
        int chunkId = chunkDAO.save(new TextChunk(docId, 0, "Deep neural representations"));

        TextChunk chunk = new TextChunk(chunkId, docId, 0, "Deep neural representations");
        embeddingService.embedChunk(chunk);

        // Persist cache to DB
        embeddingService.persistToDatabase(embeddingDAO);
        assertTrue(embeddingDAO.findByChunkId(chunkId).isPresent());

        // Create fresh service with empty cache and load from DB
        EmbeddingService freshService = new EmbeddingService(new DeterministicEmbeddingModel(64));
        assertEquals(0, freshService.getAllCachedEmbeddings().size());

        freshService.loadFromDatabase(embeddingDAO);
        assertEquals(1, freshService.getAllCachedEmbeddings().size());
        assertNotNull(freshService.getCachedEmbedding(String.valueOf(chunkId)));
    }

    @Test
    void testUnpersistedChunkDoesNotCollide() throws Exception {
        TextChunk c1 = new TextChunk(1, 0, "Artificial intelligence in medicine");
        TextChunk c2 = new TextChunk(1, 1, "Culinary recipes for Italian pasta");
        assertEquals(0, c1.getId());
        assertEquals(0, c2.getId());

        double[] v1 = embeddingService.embedChunk(c1);
        double[] v2 = embeddingService.embedChunk(c2);

        assertNotNull(v1);
        assertNotNull(v2);
        assertTrue(VectorUtils.cosineSimilarity(v1, v2) < 0.9, "Distinct unpersisted chunks must not collide");
        assertNull(embeddingService.getCachedEmbedding("0"), "Unpersisted chunks (id=0) must not be cached under key '0'");
    }
}

package com.cortexgraph.rag;

import com.cortexgraph.dao.ChunkDAO;
import com.cortexgraph.dao.DatabaseManager;
import com.cortexgraph.dao.DocumentDAO;
import com.cortexgraph.dao.EmbeddingDAO;
import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.GraphEdge;
import com.cortexgraph.model.ScoredChunk;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RAGIntegrationTest {
    @Test
    void testFullRAGPipelineWithDatabaseAndGraph(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("rag_e2e.db");
        DatabaseManager.initializeDatabase(dbUrl);

        DocumentDAO docDAO = new DocumentDAO(dbUrl);
        ChunkDAO chunkDAO = new ChunkDAO(dbUrl);
        EmbeddingDAO embeddingDAO = new EmbeddingDAO(dbUrl);

        int docId = docDAO.save(new DocumentRecord("ai_paper.txt", "/ai_paper.txt", 1000L));
        int c1Id = chunkDAO.save(new TextChunk(docId, 0, "Knowledge graphs and neural networks for deep learning representation"));
        int c2Id = chunkDAO.save(new TextChunk(docId, 1, "Knowledge graphs and neural networks for machine learning representation"));
        int c3Id = chunkDAO.save(new TextChunk(docId, 2, "Baking chocolate chip cookies in an oven"));

        EmbeddingService embeddingService = new EmbeddingService(new DeterministicEmbeddingModel(64));
        VectorSearchService searchService = new VectorSearchService();

        // 1. Embed and persist
        List<TextChunk> chunks = chunkDAO.findByDocumentId(docId);
        for (TextChunk chunk : chunks) {
            embeddingService.embedChunk(chunk);
        }
        embeddingService.persistToDatabase(embeddingDAO);

        // 2. Clear cache and reload from DB
        EmbeddingService freshEmbeddingService = new EmbeddingService(new DeterministicEmbeddingModel(64));
        assertEquals(0, freshEmbeddingService.getAllCachedEmbeddings().size());
        freshEmbeddingService.loadFromDatabase(embeddingDAO);
        assertEquals(3, freshEmbeddingService.getAllCachedEmbeddings().size());

        // 3. Query top-K
        double[] queryVec = freshEmbeddingService.embedText("knowledge graphs neural networks");
        List<ScoredChunk> topResults = searchService.topKChunks(queryVec, chunks, freshEmbeddingService, 2);

        assertEquals(2, topResults.size());
        assertTrue(topResults.get(0).getChunk().getId() == c1Id || topResults.get(0).getChunk().getId() == c2Id);

        // 4. Build similarity graph edges
        List<GraphEdge> edges = searchService.buildSimilarityEdges(chunks, freshEmbeddingService, 0.60);
        assertFalse(edges.isEmpty());
        assertEquals("SIMILAR_TO", edges.get(0).getRelationType());
        assertEquals(c1Id, edges.get(0).getSourceChunkId());
        assertEquals(c2Id, edges.get(0).getTargetChunkId());
        assertTrue(edges.get(0).getWeight() >= 0.60);
    }
}

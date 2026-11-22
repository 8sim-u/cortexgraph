package com.cortexgraph;

import com.cortexgraph.dao.ChatDAO;
import com.cortexgraph.dao.ChunkDAO;
import com.cortexgraph.dao.DatabaseManager;
import com.cortexgraph.dao.DocumentDAO;
import com.cortexgraph.dao.EmbeddingDAO;
import com.cortexgraph.model.ChatMessage;
import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.GraphEdge;
import com.cortexgraph.model.TextChunk;
import com.cortexgraph.parser.DocumentIngestor;
import com.cortexgraph.rag.DeterministicEmbeddingModel;
import com.cortexgraph.rag.EmbeddingService;
import com.cortexgraph.rag.RagChatService;
import com.cortexgraph.rag.VectorSearchService;
import com.cortexgraph.ui.model.GraphNode;
import com.cortexgraph.ui.physics.GraphPhysicsEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test verifying that all documents, chunks, vectors,
 * similarity graph edges, and chat history persist cleanly across application restarts.
 */
public class PersistenceRestartIntegrationTest {

    @Test
    void testEndToEndStateRestorationAcrossRestart(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("restart_app.db");
        DatabaseManager.initializeDatabase(dbUrl);

        int doc1Id;
        int doc2Id;
        int totalChunksSession1;

        // ==========================================
        // SESSION 1: Initial Ingestion & Interaction
        // ==========================================
        {
            DocumentDAO docDAO = new DocumentDAO(dbUrl);
            ChunkDAO chunkDAO = new ChunkDAO(dbUrl);
            ChatDAO chatDAO = new ChatDAO(dbUrl);
            EmbeddingDAO embeddingDAO = new EmbeddingDAO(dbUrl);

            EmbeddingService embeddingService = new EmbeddingService(new DeterministicEmbeddingModel(64));
            VectorSearchService searchService = new VectorSearchService();
            RagChatService chatService = new RagChatService(searchService, embeddingService, docDAO, chunkDAO, chatDAO);

            // Create 2 test files
            Path file1 = tempDir.resolve("algorithms.txt");
            Files.writeString(file1, "Graph search algorithms include breadth first search and depth first search for traversing vertices and edges");

            Path file2 = tempDir.resolve("neural_graphs.md");
            Files.writeString(file2, "Graph neural networks combine graph topology with neural embeddings for node classification and link prediction");

            try (DocumentIngestor ingestor = new DocumentIngestor()) {
                // Ingest Doc 1
                DocumentRecord doc1 = new DocumentRecord("algorithms.txt", file1.toAbsolutePath().toString(), Files.size(file1));
                doc1Id = docDAO.save(doc1);
                List<TextChunk> chunks1 = ingestor.ingestAsync(doc1Id, file1.toFile()).get();
                chunkDAO.saveAll(chunks1);

                // Ingest Doc 2
                DocumentRecord doc2 = new DocumentRecord("neural_graphs.md", file2.toAbsolutePath().toString(), Files.size(file2));
                doc2Id = docDAO.save(doc2);
                List<TextChunk> chunks2 = ingestor.ingestAsync(doc2Id, file2.toFile()).get();
                chunkDAO.saveAll(chunks2);

                List<TextChunk> allChunks = new ArrayList<>();
                allChunks.addAll(chunkDAO.findByDocumentId(doc1Id));
                allChunks.addAll(chunkDAO.findByDocumentId(doc2Id));
                totalChunksSession1 = allChunks.size();
                assertTrue(totalChunksSession1 >= 2);

                // Embed all chunks and persist
                for (TextChunk chunk : allChunks) {
                    embeddingService.embedChunk(chunk);
                }
                embeddingService.persistToDatabase(embeddingDAO);
                assertEquals(totalChunksSession1, embeddingDAO.findAll().size());

                // Build initial similarity edges
                List<GraphEdge> edges = searchService.buildSimilarityEdges(allChunks, embeddingService, 0.30);
                assertNotNull(edges);

                // Execute chat interaction
                ChatMessage answer = chatService.askQuestion("Tell me about graph search and traversal algorithms", 2);
                assertNotNull(answer);
                assertEquals("assistant", answer.getRole());
                assertTrue(answer.getContent().contains("[algorithms.txt, Page 1]"));

                List<ChatMessage> history = chatDAO.findAll();
                assertEquals(2, history.size());
            }
            // End of Session 1 - all local variables scoped and released
        }

        // ==========================================
        // SESSION 2: Simulated Application Restart
        // ==========================================
        {
            // Fresh DAOs pointing to the same persistent SQLite database
            DocumentDAO freshDocDAO = new DocumentDAO(dbUrl);
            ChunkDAO freshChunkDAO = new ChunkDAO(dbUrl);
            ChatDAO freshChatDAO = new ChatDAO(dbUrl);
            EmbeddingDAO freshEmbeddingDAO = new EmbeddingDAO(dbUrl);

            // 1. Verify Documents Restored
            List<DocumentRecord> restoredDocs = freshDocDAO.findAll();
            assertEquals(2, restoredDocs.size());
            assertTrue(restoredDocs.stream().anyMatch(d -> d.getFilename().equals("algorithms.txt")));
            assertTrue(restoredDocs.stream().anyMatch(d -> d.getFilename().equals("neural_graphs.md")));

            // 2. Verify Chunks Restored
            List<TextChunk> restoredChunks1 = freshChunkDAO.findByDocumentId(doc1Id);
            List<TextChunk> restoredChunks2 = freshChunkDAO.findByDocumentId(doc2Id);
            assertFalse(restoredChunks1.isEmpty());
            assertFalse(restoredChunks2.isEmpty());
            assertEquals(totalChunksSession1, restoredChunks1.size() + restoredChunks2.size());

            // 3. Verify SQLite Vector Persistence Restored without Re-computation
            EmbeddingService freshEmbeddingService = new EmbeddingService(new DeterministicEmbeddingModel(64));
            // Cache starts empty
            assertEquals(0, freshEmbeddingService.getAllCachedEmbeddings().size());

            // Load directly from SQLite
            freshEmbeddingService.loadFromDatabase(freshEmbeddingDAO);
            assertEquals(totalChunksSession1, freshEmbeddingService.getAllCachedEmbeddings().size());

            for (TextChunk chunk : restoredChunks1) {
                double[] vec = freshEmbeddingService.getCachedEmbedding(String.valueOf(chunk.getId()));
                assertNotNull(vec, "Vector must be restored in cache for chunk " + chunk.getId());
                assertEquals(64, vec.length);
            }

            // 4. Verify Physics Graph Reconstruction from Restored State
            VectorSearchService freshSearchService = new VectorSearchService();
            GraphPhysicsEngine freshPhysicsEngine = new GraphPhysicsEngine();
            freshPhysicsEngine.setCenterPoint(400.0, 300.0);

            List<TextChunk> allRestoredChunks = new ArrayList<>();
            allRestoredChunks.addAll(restoredChunks1);
            allRestoredChunks.addAll(restoredChunks2);

            for (TextChunk chunk : allRestoredChunks) {
                freshPhysicsEngine.addNode(new GraphNode(
                        chunk.getId(),
                        chunk.getDocumentId(),
                        chunk.getDocumentId() == doc1Id ? "algorithms.txt" : "neural_graphs.md",
                        chunk.getPageNumber(),
                        chunk.getContent(),
                        400.0,
                        300.0
                ));
            }
            assertEquals(totalChunksSession1, freshPhysicsEngine.getNodes().size());

            // Rebuild edges from cached vectors
            List<GraphEdge> restoredEdges = freshSearchService.buildSimilarityEdges(allRestoredChunks, freshEmbeddingService, 0.30);
            for (GraphEdge edge : restoredEdges) {
                freshPhysicsEngine.addEdge(edge);
            }
            // Physics simulation runs smoothly on restored graph
            freshPhysicsEngine.step(0.016);
            for (GraphNode node : freshPhysicsEngine.getNodes()) {
                assertFalse(Double.isNaN(node.getX()));
                assertFalse(Double.isNaN(node.getY()));
            }

            // 5. Verify Previous Chat History Restored
            List<ChatMessage> restoredHistory = freshChatDAO.findAll();
            assertEquals(2, restoredHistory.size());
            assertEquals("user", restoredHistory.get(0).getRole());
            assertEquals("assistant", restoredHistory.get(1).getRole());
            assertTrue(restoredHistory.get(1).getContent().contains("[algorithms.txt, Page 1]"));

            // 6. Verify New Chat Interaction Works Immediately on Restored State
            RagChatService freshChatService = new RagChatService(
                    freshSearchService, freshEmbeddingService,
                    freshDocDAO, freshChunkDAO, freshChatDAO
            );

            ChatMessage newAnswer = freshChatService.askQuestion("How do neural networks process graph topology?", 2);
            assertNotNull(newAnswer);
            assertEquals("assistant", newAnswer.getRole());
            assertTrue(newAnswer.getContent().contains("[neural_graphs.md, Page 1]"),
                    "Response must cite restored second document");

            // Chat history now has 4 messages total
            List<ChatMessage> updatedHistory = freshChatDAO.findAll();
            assertEquals(4, updatedHistory.size());
        }
    }
}

package com.cortexgraph.ui;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CortexGraphAppIntegrationTest {

    @Test
    void testFullWorkflowIngestionPhysicsAndRagChat(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("e2e_ui.db");
        DatabaseManager.initializeDatabase(dbUrl);

        DocumentDAO docDAO = new DocumentDAO(dbUrl);
        ChunkDAO chunkDAO = new ChunkDAO(dbUrl);
        ChatDAO chatDAO = new ChatDAO(dbUrl);
        EmbeddingDAO embeddingDAO = new EmbeddingDAO(dbUrl);

        EmbeddingService embeddingService = new EmbeddingService(new DeterministicEmbeddingModel(64));
        VectorSearchService searchService = new VectorSearchService();
        RagChatService chatService = new RagChatService(searchService, embeddingService, docDAO, chunkDAO, chatDAO);

        // 1. Create temporary sample markdown document
        Path sampleDoc = tempDir.resolve("cortex_syllabus.md");
        Files.writeString(sampleDoc, """
                # CortexGraph Architecture
                
                CortexGraph combines Java 21 streams, 2D force-directed physics graph algorithms,
                and vector embeddings for grounded question answering with page citations.
                
                ## Physics Simulation
                
                The canvas runs an AnimationTimer at 60 frames per second using Hooke's Law
                for spring attraction and Coulomb repulsion to organize knowledge clusters.
                """);

        // 2. Ingest document asynchronously
        try (DocumentIngestor ingestor = new DocumentIngestor()) {
            DocumentRecord doc = new DocumentRecord("cortex_syllabus.md", sampleDoc.toAbsolutePath().toString(), Files.size(sampleDoc));
            int docId = docDAO.save(doc);

            List<TextChunk> chunks = ingestor.ingestAsync(docId, sampleDoc.toFile()).get();
            assertFalse(chunks.isEmpty());
            chunkDAO.saveAll(chunks);

            List<TextChunk> savedChunks = chunkDAO.findByDocumentId(docId);
            assertEquals(chunks.size(), savedChunks.size());

            // 3. Generate and persist embeddings
            for (TextChunk chunk : savedChunks) {
                embeddingService.embedChunk(chunk);
            }
            embeddingService.persistToDatabase(embeddingDAO);
            assertEquals(savedChunks.size(), embeddingDAO.findAll().size());

            // 4. Populate 2D physics graph engine
            GraphPhysicsEngine physicsEngine = new GraphPhysicsEngine();
            physicsEngine.setCenterPoint(400.0, 300.0);

            for (int i = 0; i < savedChunks.size(); i++) {
                TextChunk chunk = savedChunks.get(i);
                physicsEngine.addNode(new GraphNode(
                        chunk.getId(),
                        chunk.getDocumentId(),
                        doc.getFilename(),
                        chunk.getPageNumber(),
                        chunk.getContent(),
                        400.0 + i * 20.0,
                        300.0 + i * 20.0
                ));
            }
            assertEquals(savedChunks.size(), physicsEngine.getNodes().size());

            // 5. Build similarity edges
            List<GraphEdge> edges = searchService.buildSimilarityEdges(savedChunks, embeddingService, 0.40);
            for (GraphEdge edge : edges) {
                physicsEngine.addEdge(edge);
            }

            // 6. Step physics simulation and verify stability
            for (int step = 0; step < 60; step++) {
                physicsEngine.step(0.016);
            }

            for (GraphNode node : physicsEngine.getNodes()) {
                assertFalse(Double.isNaN(node.getX()));
                assertFalse(Double.isNaN(node.getY()));
                assertTrue(Math.abs(node.getVx()) < 50.0, "Velocity must be dampened");
                assertTrue(Math.abs(node.getVy()) < 50.0, "Velocity must be dampened");
            }

            // 7. Execute grounded RAG chat
            ChatMessage answer = chatService.askQuestion("What physics laws are used in the simulation?", 2);
            assertNotNull(answer);
            assertEquals("assistant", answer.getRole());
            assertTrue(answer.getContent().contains("[cortex_syllabus.md, Page 1]"),
                    "Answer must cite source filename and page");
            assertTrue(answer.getContent().toLowerCase().contains("hooke") || answer.getContent().toLowerCase().contains("coulomb"),
                    "Answer must contain grounded excerpt");

            // 8. Verify chat history
            List<ChatMessage> history = chatDAO.findAll();
            assertEquals(2, history.size());
            assertEquals("user", history.get(0).getRole());
            assertEquals("assistant", history.get(1).getRole());
        }
    }

    @Test
    void testPopulateDocumentsListFromDatabaseOnStartup(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("startup_docs.db");
        DatabaseManager.initializeDatabase(dbUrl);

        DocumentDAO docDAO = new DocumentDAO(dbUrl);
        docDAO.save(new DocumentRecord("lecture1.pdf", "/docs/lecture1.pdf", 1024L));
        docDAO.save(new DocumentRecord("lecture2.txt", "/docs/lecture2.txt", 2048L));

        CortexGraphApp app = new CortexGraphApp();
        app.setDbUrl(dbUrl);
        app.init();

        try {
            // Initially documentsObservable is populated via populateDocumentsList()
            app.populateDocumentsList();
            assertEquals(2, app.getDocumentsObservable().size());
            assertTrue(app.getDocumentsObservable().stream().anyMatch(s -> s.contains("lecture1.pdf")));
            assertTrue(app.getDocumentsObservable().stream().anyMatch(s -> s.contains("lecture2.txt")));
        } finally {
            app.stop();
        }
    }
}

package com.cortexgraph.rag;

import com.cortexgraph.dao.ChatDAO;
import com.cortexgraph.dao.ChunkDAO;
import com.cortexgraph.dao.DatabaseManager;
import com.cortexgraph.dao.DocumentDAO;
import com.cortexgraph.model.ChatMessage;
import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RagChatServiceTest {

    private RagChatService chatService;
    private DocumentDAO docDAO;
    private ChunkDAO chunkDAO;
    private ChatDAO chatDAO;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("chat_test.db");
        DatabaseManager.initializeDatabase(dbUrl);

        docDAO = new DocumentDAO(dbUrl);
        chunkDAO = new ChunkDAO(dbUrl);
        chatDAO = new ChatDAO(dbUrl);

        EmbeddingService embeddingService = new EmbeddingService(new DeterministicEmbeddingModel(32));
        VectorSearchService searchService = new VectorSearchService();

        chatService = new RagChatService(searchService, embeddingService, docDAO, chunkDAO, chatDAO);
    }

    @Test
    void testAskQuestionWithCitations() throws Exception {
        int docId = docDAO.save(new DocumentRecord("syllabus.pdf", "/docs/syllabus.pdf", 500L));
        chunkDAO.save(new TextChunk(docId, 0, "Object oriented programming and stream I/O pipelines in Java", 2));
        chunkDAO.save(new TextChunk(docId, 1, "Baking bread with flour yeast water", 5));

        ChatMessage response = chatService.askQuestion("Tell me about Java stream pipelines", 2);

        assertNotNull(response);
        assertEquals("assistant", response.getRole());
        assertTrue(response.getContent().contains("[syllabus.pdf, Page 2]"), "Must include grounded source citation");
        assertTrue(response.getContent().contains("stream I/O pipelines"), "Must include relevant chunk content");

        // Verify history in database
        List<ChatMessage> history = chatDAO.findAll();
        assertEquals(2, history.size());
        assertEquals("user", history.get(0).getRole());
        assertEquals("assistant", history.get(1).getRole());
    }

    @Test
    void testAskQuestionWithEmptyDatabase() throws Exception {
        ChatMessage response = chatService.askQuestion("Any question", 3);
        assertNotNull(response);
        assertEquals("assistant", response.getRole());
        assertTrue(response.getContent().contains("could not find"), "Should return friendly fallback message");
    }

    @Test
    void testInvalidQuestionThrows() {
        assertThrows(IllegalArgumentException.class, () -> chatService.askQuestion(null, 2));
        assertThrows(IllegalArgumentException.class, () -> chatService.askQuestion("   ", 2));
    }
}

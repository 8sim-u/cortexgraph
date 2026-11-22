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
        assertTrue(response.getContent().startsWith("Answer:\n"), "Response must start with Answer section");
        assertTrue(response.getContent().contains("Sources & Citations:"), "Response must contain Citations section");
        assertTrue(response.getContent().contains("[syllabus.pdf, Page 2]"), "Must include grounded source citation");
        assertTrue(response.getContent().contains("stream I/O pipelines"), "Must include relevant chunk content");

        // Verify history in database
        List<ChatMessage> history = chatDAO.findAll();
        assertEquals(2, history.size());
        assertEquals("user", history.get(0).getRole());
        assertEquals("assistant", history.get(1).getRole());
    }

    @Test
    void testDegreeSumPropertyRanksPageOneWithCleanAnswer() throws Exception {
        int docId = docDAO.save(new DocumentRecord("discrete_math.pdf", "/docs/discrete_math.pdf", 1200L));
        chunkDAO.save(new TextChunk(docId, 0, "Introduction to graph theory and connected components.", 1));
        chunkDAO.save(new TextChunk(docId, 1, "The degree of a vertex represents incident edges in an undirected graph.", 2));
        chunkDAO.save(new TextChunk(docId, 2, "The degree sum property states that the sum of degrees of all vertices in an undirected graph equals twice the number of edges: sum(deg(v)) = 2|E|.", 3));

        // Query with common stopwords ("what", "is", "the", "in", "a")
        ChatMessage response = chatService.askQuestion("What is the degree sum property in a graph?", 3);

        assertNotNull(response);
        String content = response.getContent();

        // 1. Verify clean readable answer formatted above citations
        assertTrue(content.startsWith("Answer:\n"), "Readable answer must appear at the top");
        assertTrue(content.contains("Sources & Citations:\n"), "Sources & Citations section must follow");
        assertTrue(content.contains("The degree sum property states that the sum of degrees"), "Answer summary must capture the key definition");

        // 2. Verify Page 3 is ranked #1 (appears before Page 2 or Page 1)
        int page3Index = content.indexOf("[discrete_math.pdf, Page 3]");
        int page2Index = content.indexOf("[discrete_math.pdf, Page 2]");
        assertTrue(page3Index != -1, "Page 3 must be cited");
        assertTrue(page2Index == -1 || page3Index < page2Index, "Page 3 must be ranked #1 above Page 2");
    }

    @Test
    void testStopwordFilteringInKeywords() {
        List<String> keywords = chatService.extractKeywords("What is the degree sum property in a graph of and for vertices?");
        assertFalse(keywords.contains("what"));
        assertFalse(keywords.contains("is"));
        assertFalse(keywords.contains("the"));
        assertFalse(keywords.contains("in"));
        assertFalse(keywords.contains("a"));
        assertFalse(keywords.contains("of"));
        assertFalse(keywords.contains("and"));
        assertFalse(keywords.contains("for"));

        assertTrue(keywords.contains("degree"));
        assertTrue(keywords.contains("sum"));
        assertTrue(keywords.contains("property"));
        assertTrue(keywords.contains("graph"));
        assertTrue(keywords.contains("vertices"));
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

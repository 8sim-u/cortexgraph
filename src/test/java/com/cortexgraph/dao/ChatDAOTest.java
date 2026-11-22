package com.cortexgraph.dao;

import com.cortexgraph.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChatDAOTest {

    private ChatDAO chatDAO;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("chat_test.db");
        DatabaseManager.initializeDatabase(dbUrl);
        chatDAO = new ChatDAO(dbUrl);
    }

    @Test
    void testSaveFindAndClearMessages() throws Exception {
        int id1 = chatDAO.save(new ChatMessage("user", "What is CortexGraph?"));
        int id2 = chatDAO.save(new ChatMessage("assistant", "It is a knowledge vault!"));
        assertTrue(id1 > 0);
        assertTrue(id2 > id1);

        List<ChatMessage> history = chatDAO.findAll();
        assertEquals(2, history.size());
        assertEquals("user", history.get(0).getSender());
        assertEquals("What is CortexGraph?", history.get(0).getContent());
        assertNotNull(history.get(0).getTimestamp());

        assertEquals("assistant", history.get(1).getSender());
        assertEquals("It is a knowledge vault!", history.get(1).getContent());

        chatDAO.clearHistory();
        assertTrue(chatDAO.findAll().isEmpty());
    }
}

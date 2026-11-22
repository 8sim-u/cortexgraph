package com.cortexgraph.dao;

import com.cortexgraph.exception.VaultException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseManagerTest {

    @Test
    void testInitializeDatabaseCreatesTables(@TempDir Path tempDir) throws Exception {
        String testDb = "jdbc:sqlite:" + tempDir.resolve("test_cortex.db");
        DatabaseManager.initializeDatabase(testDb);

        try (Connection conn = DatabaseManager.getConnection(testDb);
             Statement stmt = conn.createStatement()) {

            // Check documents table
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='documents'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }

            // Check chunks table
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='chunks'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }

            // Check chat_history table
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='chat_history'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }

        // Verify idempotency
        assertDoesNotThrow(() -> DatabaseManager.initializeDatabase(testDb));
    }

    @Test
    void testDefaultDatabaseUrlConstant() {
        assertEquals("jdbc:sqlite:cortexgraph.db", DatabaseManager.DEFAULT_DB_URL);
    }
}

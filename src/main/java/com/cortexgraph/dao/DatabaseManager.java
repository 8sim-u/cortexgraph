package com.cortexgraph.dao;

import com.cortexgraph.exception.VaultException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages SQLite database connections and schema initialization.
 */
public class DatabaseManager {

    public static final String DEFAULT_DB_URL = "jdbc:sqlite:cortexgraph.db";

    private DatabaseManager() {
        // Utility class with static methods
    }

    /**
     * Opens a new database connection for the default database URL.
     */
    public static Connection getConnection() throws VaultException {
        return getConnection(DEFAULT_DB_URL);
    }

    /**
     * Opens a new database connection for the specified database URL.
     */
    public static Connection getConnection(String dbUrl) throws VaultException {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(dbUrl);
            // Enable foreign key constraints in SQLite
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }
            return connection;
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException closeEx) {
                    e.addSuppressed(closeEx);
                }
            }
            throw new VaultException("Failed to connect to database at: " + dbUrl, e);
        }
    }

    /**
     * Initializes the default database tables if they do not exist.
     */
    public static void initializeDatabase() throws VaultException {
        initializeDatabase(DEFAULT_DB_URL);
    }

    /**
     * Initializes the database tables for the given database URL.
     */
    public static void initializeDatabase(String dbUrl) throws VaultException {
        String createDocumentsTable = """
            CREATE TABLE IF NOT EXISTS documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                filename TEXT NOT NULL,
                file_path TEXT NOT NULL,
                file_size INTEGER NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;

        String createChunksTable = """
            CREATE TABLE IF NOT EXISTS chunks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                document_id INTEGER NOT NULL,
                chunk_index INTEGER NOT NULL,
                content TEXT NOT NULL,
                page_number INTEGER DEFAULT 1,
                FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
            );
            """;

        String createChatHistoryTable = """
            CREATE TABLE IF NOT EXISTS chat_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;

        String createEmbeddingsTable = """
            CREATE TABLE IF NOT EXISTS embeddings (
                chunk_id INTEGER PRIMARY KEY,
                vector BLOB NOT NULL,
                FOREIGN KEY (chunk_id) REFERENCES chunks(id) ON DELETE CASCADE
            );
            """;

        String createChunksIndex = """
            CREATE INDEX IF NOT EXISTS idx_chunks_document_id ON chunks(document_id);
            """;

        try (Connection conn = getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createDocumentsTable);
            stmt.execute(createChunksTable);
            stmt.execute(createChatHistoryTable);
            stmt.execute(createEmbeddingsTable);
            stmt.execute(createChunksIndex);
        } catch (SQLException e) {
            throw new VaultException("Failed to initialize database tables at: " + dbUrl, e);
        }
    }
}

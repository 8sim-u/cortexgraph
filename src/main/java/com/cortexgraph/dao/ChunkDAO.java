package com.cortexgraph.dao;

import com.cortexgraph.exception.VaultException;
import com.cortexgraph.model.TextChunk;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for TextChunk operations.
 */
public class ChunkDAO {

    private final String dbUrl;

    public ChunkDAO() {
        this(DatabaseManager.DEFAULT_DB_URL);
    }

    public ChunkDAO(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    /**
     * Saves a single TextChunk and returns the auto-generated ID.
     */
    public int save(TextChunk chunk) throws VaultException {
        String sql = "INSERT INTO chunks (document_id, chunk_index, content) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, chunk.getDocumentId());
            stmt.setInt(2, chunk.getChunkIndex());
            stmt.setString(3, chunk.getContent());
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                } else {
                    throw new VaultException("Creating chunk failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            throw new VaultException("Error saving chunk for document id: " + chunk.getDocumentId(), e);
        }
    }

    /**
     * Saves a list of TextChunks in a single batch transaction.
     */
    public void saveAll(List<TextChunk> chunks) throws VaultException {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO chunks (document_id, chunk_index, content) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection(dbUrl)) {
            try {
                conn.setAutoCommit(false);
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (TextChunk chunk : chunks) {
                        stmt.setInt(1, chunk.getDocumentId());
                        stmt.setInt(2, chunk.getChunkIndex());
                        stmt.setString(3, chunk.getContent());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw new VaultException("Failed to batch save chunks, transaction rolled back", e);
            }
        } catch (SQLException e) {
            throw new VaultException("Database error during batch chunk insert", e);
        }
    }

    /**
     * Retrieves all text chunks belonging to a document, ordered by chunk index.
     */
    public List<TextChunk> findByDocumentId(int documentId) throws VaultException {
        String sql = "SELECT id, document_id, chunk_index, content FROM chunks WHERE document_id = ? ORDER BY chunk_index ASC";
        List<TextChunk> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, documentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new TextChunk(
                            rs.getInt("id"),
                            rs.getInt("document_id"),
                            rs.getInt("chunk_index"),
                            rs.getString("content")
                    ));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new VaultException("Error finding chunks for document id: " + documentId, e);
        }
    }

    /**
     * Deletes all chunks associated with a document ID.
     */
    public boolean deleteByDocumentId(int documentId) throws VaultException {
        String sql = "DELETE FROM chunks WHERE document_id = ?";
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, documentId);
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            throw new VaultException("Error deleting chunks for document id: " + documentId, e);
        }
    }
}

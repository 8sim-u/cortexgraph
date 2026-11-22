package com.cortexgraph.dao;

import com.cortexgraph.exception.VaultException;
import com.cortexgraph.rag.VectorUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object for persisting and retrieving chunk vector embeddings in SQLite.
 */
public class EmbeddingDAO {

    private final String dbUrl;

    public EmbeddingDAO() {
        this(DatabaseManager.DEFAULT_DB_URL);
    }

    public EmbeddingDAO(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    /**
     * Persists or replaces the embedding vector for a chunk.
     */
    public void save(int chunkId, double[] vector) throws VaultException {
        String sql = "INSERT OR REPLACE INTO embeddings (chunk_id, vector) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, chunkId);
            stmt.setBytes(2, VectorUtils.toByteArray(vector));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new VaultException("Error saving embedding for chunk id: " + chunkId, e);
        }
    }

    /**
     * Persists multiple embeddings in a single atomic batch transaction.
     */
    public void saveAll(Map<Integer, double[]> embeddings) throws VaultException {
        if (embeddings == null || embeddings.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO embeddings (chunk_id, vector) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection(dbUrl)) {
            try {
                conn.setAutoCommit(false);
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (Map.Entry<Integer, double[]> entry : embeddings.entrySet()) {
                        stmt.setInt(1, entry.getKey());
                        stmt.setBytes(2, VectorUtils.toByteArray(entry.getValue()));
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
                throw new VaultException("Failed to batch save embeddings, transaction rolled back", e);
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            throw new VaultException("Database error during batch embedding insert", e);
        }
    }

    /**
     * Finds the embedding vector for a specific chunk ID.
     */
    public Optional<double[]> findByChunkId(int chunkId) throws VaultException {
        String sql = "SELECT vector FROM embeddings WHERE chunk_id = ?";
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, chunkId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    byte[] bytes = rs.getBytes("vector");
                    return Optional.of(VectorUtils.fromByteArray(bytes));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new VaultException("Error finding embedding for chunk id: " + chunkId, e);
        }
    }

    /**
     * Retrieves all persisted chunk embeddings into a Map of chunkId -> vector.
     */
    public Map<Integer, double[]> findAll() throws VaultException {
        String sql = "SELECT chunk_id, vector FROM embeddings";
        Map<Integer, double[]> map = new HashMap<>();
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int chunkId = rs.getInt("chunk_id");
                byte[] bytes = rs.getBytes("vector");
                map.put(chunkId, VectorUtils.fromByteArray(bytes));
            }
            return map;
        } catch (SQLException e) {
            throw new VaultException("Error retrieving all embeddings", e);
        }
    }

    /**
     * Deletes the embedding for a specific chunk.
     */
    public boolean deleteByChunkId(int chunkId) throws VaultException {
        String sql = "DELETE FROM embeddings WHERE chunk_id = ?";
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, chunkId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new VaultException("Error deleting embedding for chunk id: " + chunkId, e);
        }
    }
}

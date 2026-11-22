package com.cortexgraph.dao;

import com.cortexgraph.exception.VaultException;
import com.cortexgraph.model.DocumentRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for DocumentRecord operations.
 */
public class DocumentDAO {

    private final String dbUrl;

    public DocumentDAO() {
        this(DatabaseManager.DEFAULT_DB_URL);
    }

    public DocumentDAO(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    /**
     * Saves a new DocumentRecord and returns the auto-generated primary key ID.
     */
    public int save(DocumentRecord doc) throws VaultException {
        String sql = "INSERT INTO documents (filename, file_path, file_size) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, doc.getFilename());
            stmt.setString(2, doc.getFilePath());
            stmt.setLong(3, doc.getFileSize());
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new VaultException("Creating document failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            throw new VaultException("Error saving document: " + doc.getFilename(), e);
        }
    }

    /**
     * Finds a DocumentRecord by its primary key ID.
     */
    public Optional<DocumentRecord> findById(int id) throws VaultException {
        String sql = "SELECT id, filename, file_path, file_size, created_at FROM documents WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToDocument(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new VaultException("Error finding document by id: " + id, e);
        }
    }

    /**
     * Retrieves all documents ordered by ID descending.
     */
    public List<DocumentRecord> findAll() throws VaultException {
        String sql = "SELECT id, filename, file_path, file_size, created_at FROM documents ORDER BY id DESC";
        List<DocumentRecord> documents = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                documents.add(mapRowToDocument(rs));
            }
            return documents;
        } catch (SQLException e) {
            throw new VaultException("Error retrieving all documents", e);
        }
    }

    /**
     * Deletes a document by ID. Returns true if a record was removed.
     */
    public boolean delete(int id) throws VaultException {
        String sql = "DELETE FROM documents WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            throw new VaultException("Error deleting document with id: " + id, e);
        }
    }

    private DocumentRecord mapRowToDocument(ResultSet rs) throws SQLException {
        return new DocumentRecord(
                rs.getInt("id"),
                rs.getString("filename"),
                rs.getString("file_path"),
                rs.getLong("file_size"),
                rs.getString("created_at")
        );
    }
}

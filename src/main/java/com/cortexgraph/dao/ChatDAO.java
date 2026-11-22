package com.cortexgraph.dao;

import com.cortexgraph.exception.VaultException;
import com.cortexgraph.model.ChatMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for ChatMessage history operations.
 */
public class ChatDAO {

    private final String dbUrl;

    public ChatDAO() {
        this(DatabaseManager.DEFAULT_DB_URL);
    }

    public ChatDAO(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    /**
     * Saves a chat message and returns the auto-generated primary key ID.
     */
    public int save(ChatMessage message) throws VaultException {
        String sql = "INSERT INTO chat_history (sender, content) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, message.getSender());
            stmt.setString(2, message.getContent());
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new VaultException("Creating chat message failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            throw new VaultException("Error saving chat message from: " + message.getSender(), e);
        }
    }

    /**
     * Retrieves all chat messages in chronological order (by ID ascending).
     */
    public List<ChatMessage> findAll() throws VaultException {
        String sql = "SELECT id, sender, content, timestamp FROM chat_history ORDER BY id ASC";
        List<ChatMessage> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new ChatMessage(
                        rs.getInt("id"),
                        rs.getString("sender"),
                        rs.getString("content"),
                        rs.getString("timestamp")
                ));
            }
            return list;
        } catch (SQLException e) {
            throw new VaultException("Error retrieving chat history", e);
        }
    }

    /**
     * Clears all stored chat messages from history.
     */
    public void clearHistory() throws VaultException {
        String sql = "DELETE FROM chat_history";
        try (Connection conn = DatabaseManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new VaultException("Error clearing chat history", e);
        }
    }

    public void clear() throws VaultException {
        clearHistory();
    }
}

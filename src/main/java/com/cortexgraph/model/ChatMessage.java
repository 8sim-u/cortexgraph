package com.cortexgraph.model;

import java.util.Objects;

/**
 * Represents a chat message in the conversation history.
 */
public class ChatMessage {
    private final int id;
    private final String sender;
    private final String content;
    private final String timestamp;

    /**
     * Constructor for creating a new ChatMessage before persistence.
     */
    public ChatMessage(String sender, String content) {
        this(0, sender, content, null);
    }

    /**
     * Full constructor for existing messages retrieved from the database.
     */
    public ChatMessage(int id, String sender, String content, String timestamp) {
        this.id = id;
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public String getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatMessage that = (ChatMessage) o;
        return id == that.id &&
               Objects.equals(sender, that.sender) &&
               Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sender, content);
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "id=" + id +
                ", sender='" + sender + '\'' +
                ", content='" + content + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}

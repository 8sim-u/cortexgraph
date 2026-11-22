package com.cortexgraph.model;

import java.util.Objects;

/**
 * Represents a chunk of text extracted from a parsed document.
 */
public class TextChunk {
    private final int id;
    private final int documentId;
    private final int chunkIndex;
    private final String content;

    /**
     * Constructor for creating a new TextChunk before persistence.
     */
    public TextChunk(int documentId, int chunkIndex, String content) {
        this(0, documentId, chunkIndex, content);
    }

    /**
     * Full constructor for existing chunks retrieved from the database.
     */
    public TextChunk(int id, int documentId, int chunkIndex, String content) {
        this.id = id;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    public int getId() {
        return id;
    }

    public int getDocumentId() {
        return documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextChunk textChunk = (TextChunk) o;
        return id == textChunk.id &&
               documentId == textChunk.documentId &&
               chunkIndex == textChunk.chunkIndex &&
               Objects.equals(content, textChunk.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, documentId, chunkIndex, content);
    }

    @Override
    public String toString() {
        return "TextChunk{" +
                "id=" + id +
                ", documentId=" + documentId +
                ", chunkIndex=" + chunkIndex +
                ", content='" + (content != null && content.length() > 30 ? content.substring(0, 30) + "..." : content) + '\'' +
                '}';
    }
}

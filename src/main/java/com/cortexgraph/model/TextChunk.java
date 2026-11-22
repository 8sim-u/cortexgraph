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
    private final int pageNumber;

    /**
     * Constructor for creating a new TextChunk before persistence, defaulting page to 1.
     */
    public TextChunk(int documentId, int chunkIndex, String content) {
        this(0, documentId, chunkIndex, content, 1);
    }

    /**
     * Constructor for creating a new TextChunk with explicit page number before persistence.
     */
    public TextChunk(int documentId, int chunkIndex, String content, int pageNumber) {
        this(0, documentId, chunkIndex, content, pageNumber);
    }

    /**
     * Full constructor for existing chunks retrieved from the database, defaulting page to 1.
     */
    public TextChunk(int id, int documentId, int chunkIndex, String content) {
        this(id, documentId, chunkIndex, content, 1);
    }

    /**
     * Full constructor for existing chunks with explicit page number.
     */
    public TextChunk(int id, int documentId, int chunkIndex, String content, int pageNumber) {
        this.id = id;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.pageNumber = pageNumber;
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

    public int getPageNumber() {
        return pageNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextChunk textChunk = (TextChunk) o;
        return id == textChunk.id &&
               documentId == textChunk.documentId &&
               chunkIndex == textChunk.chunkIndex &&
               pageNumber == textChunk.pageNumber &&
               Objects.equals(content, textChunk.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, documentId, chunkIndex, content, pageNumber);
    }

    @Override
    public String toString() {
        return "TextChunk{" +
                "id=" + id +
                ", documentId=" + documentId +
                ", chunkIndex=" + chunkIndex +
                ", pageNumber=" + pageNumber +
                ", content='" + (content != null && content.length() > 30 ? content.substring(0, 30) + "..." : content) + '\'' +
                '}';
    }
}

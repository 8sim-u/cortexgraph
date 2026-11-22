package com.cortexgraph.model;

import java.util.Objects;

/**
 * Represents a document registered in the CortexGraph vault.
 */
public class DocumentRecord {
    private final int id;
    private final String filename;
    private final String filePath;
    private final long fileSize;
    private final String createdAt;

    /**
     * Constructor for creating a new DocumentRecord before persisting to the database.
     */
    public DocumentRecord(String filename, String filePath, long fileSize) {
        this(0, filename, filePath, fileSize, null);
    }

    /**
     * Full constructor for existing records retrieved from the database.
     */
    public DocumentRecord(int id, String filename, String filePath, long fileSize, String createdAt) {
        this.id = id;
        this.filename = filename;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentRecord that = (DocumentRecord) o;
        return id == that.id &&
               fileSize == that.fileSize &&
               Objects.equals(filename, that.filename) &&
               Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, filename, filePath, fileSize);
    }

    @Override
    public String toString() {
        return "DocumentRecord{" +
                "id=" + id +
                ", filename='" + filename + '\'' +
                ", filePath='" + filePath + '\'' +
                ", fileSize=" + fileSize +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}

package com.cortexgraph.model;

import java.util.Objects;

/**
 * Associates a TextChunk with its similarity search score.
 */
public class ScoredChunk {
    private final TextChunk chunk;
    private final double score;

    public ScoredChunk(TextChunk chunk, double score) {
        this.chunk = chunk;
        this.score = score;
    }

    public TextChunk getChunk() {
        return chunk;
    }

    public double getScore() {
        return score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScoredChunk that = (ScoredChunk) o;
        return Double.compare(that.score, score) == 0 && Objects.equals(chunk, that.chunk);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunk, score);
    }

    @Override
    public String toString() {
        return "ScoredChunk{" +
                "chunkId=" + (chunk != null ? chunk.getId() : "null") +
                ", score=" + String.format("%.4f", score) +
                '}';
    }
}

package com.cortexgraph.model;

import java.util.Objects;

/**
 * Represents a relationship edge between two chunks in the knowledge graph.
 */
public class GraphEdge {

    public static final String DEFAULT_RELATION = "SIMILAR_TO";

    private final int sourceChunkId;
    private final int targetChunkId;
    private final double weight;
    private final String relationType;

    /**
     * Creates an edge with default relation type "SIMILAR_TO".
     */
    public GraphEdge(int sourceChunkId, int targetChunkId, double weight) {
        this(sourceChunkId, targetChunkId, weight, DEFAULT_RELATION);
    }

    /**
     * Creates an edge with explicit relation type.
     */
    public GraphEdge(int sourceChunkId, int targetChunkId, double weight, String relationType) {
        this.sourceChunkId = sourceChunkId;
        this.targetChunkId = targetChunkId;
        this.weight = weight;
        this.relationType = relationType != null ? relationType : DEFAULT_RELATION;
    }

    public int getSourceChunkId() {
        return sourceChunkId;
    }

    public int getTargetChunkId() {
        return targetChunkId;
    }

    public double getWeight() {
        return weight;
    }

    public String getRelationType() {
        return relationType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphEdge graphEdge = (GraphEdge) o;
        return sourceChunkId == graphEdge.sourceChunkId &&
               targetChunkId == graphEdge.targetChunkId &&
               Double.compare(graphEdge.weight, weight) == 0 &&
               Objects.equals(relationType, graphEdge.relationType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceChunkId, targetChunkId, weight, relationType);
    }

    @Override
    public String toString() {
        return "GraphEdge{" +
                "sourceChunkId=" + sourceChunkId +
                " -[" + relationType + " (" + String.format("%.3f", weight) + ")]-> " +
                "targetChunkId=" + targetChunkId +
                '}';
    }
}

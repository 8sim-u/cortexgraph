package com.cortexgraph.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GraphEdgeTest {

    @Test
    void testGraphEdgeModel() {
        GraphEdge edge = new GraphEdge(10, 20, 0.88);
        assertEquals(10, edge.getSourceChunkId());
        assertEquals(20, edge.getTargetChunkId());
        assertEquals(0.88, edge.getWeight(), 1e-6);
        assertEquals("SIMILAR_TO", edge.getRelationType());

        GraphEdge customEdge = new GraphEdge(10, 20, 0.95, "PREREQUISITE_OF");
        assertEquals("PREREQUISITE_OF", customEdge.getRelationType());
        assertNotNull(edge.toString());

        GraphEdge same = new GraphEdge(10, 20, 0.88, "SIMILAR_TO");
        assertEquals(edge, same);
        assertEquals(edge.hashCode(), same.hashCode());
    }

    @Test
    void testScoredChunkModel() {
        TextChunk chunk = new TextChunk(1, 0, "Content");
        ScoredChunk scored = new ScoredChunk(chunk, 0.85);
        assertSame(chunk, scored.getChunk());
        assertEquals(0.85, scored.getScore(), 1e-6);
        assertNotNull(scored.toString());
    }
}

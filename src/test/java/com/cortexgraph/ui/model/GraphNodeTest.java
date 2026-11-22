package com.cortexgraph.ui.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GraphNodeTest {

    @Test
    void testNodeInitializationAndProperties() {
        GraphNode node = new GraphNode(101, 1, "doc.pdf", 3, "Extracted text chunk", 100.0, 200.0);

        assertEquals(101, node.getChunkId());
        assertEquals(1, node.getDocumentId());
        assertEquals("doc.pdf", node.getDocumentName());
        assertEquals(3, node.getPageNumber());
        assertEquals("Extracted text chunk", node.getContent());
        assertEquals(100.0, node.getX(), 1e-6);
        assertEquals(200.0, node.getY(), 1e-6);
        assertEquals(0.0, node.getVx(), 1e-6);
        assertEquals(0.0, node.getVy(), 1e-6);
        assertFalse(node.isPinned());
        assertFalse(node.isHighlighted());
        assertTrue(node.getRadius() > 0);
    }

    @Test
    void testForceApplicationAndPhysicsUpdate() {
        GraphNode node = new GraphNode(1, 1, "doc.txt", 1, "Content", 0.0, 0.0);

        node.applyForce(10.0, 20.0);
        // dt = 1.0, damping = 0.9, maxVelocity = 50.0
        node.update(1.0, 0.9, 50.0);

        // Position moved
        assertEquals(10.0, node.getX(), 1e-6);
        assertEquals(20.0, node.getY(), 1e-6);
        // Velocity damped
        assertEquals(9.0, node.getVx(), 1e-6);
        assertEquals(18.0, node.getVy(), 1e-6);
    }

    @Test
    void testPinnedNodeDoesNotMove() {
        GraphNode node = new GraphNode(1, 1, "doc.txt", 1, "Content", 50.0, 50.0);
        node.setPinned(true);
        node.applyForce(100.0, 100.0);
        node.update(1.0, 0.9, 50.0);

        assertEquals(50.0, node.getX(), 1e-6);
        assertEquals(50.0, node.getY(), 1e-6);
        assertEquals(0.0, node.getVx(), 1e-6);
        assertEquals(0.0, node.getVy(), 1e-6);
    }

    @Test
    void testDistanceAndHitTesting() {
        GraphNode n1 = new GraphNode(1, 1, "doc.txt", 1, "Content", 0.0, 0.0);
        GraphNode n2 = new GraphNode(2, 1, "doc.txt", 1, "Content", 30.0, 40.0);

        assertEquals(50.0, n1.distanceTo(n2), 1e-6);
        assertEquals(50.0, n2.distanceTo(n1), 1e-6);

        // Contains hit test
        n1.setRadius(15.0);
        assertTrue(n1.contains(0.0, 0.0));
        assertTrue(n1.contains(10.0, 10.0));
        assertFalse(n1.contains(20.0, 20.0));
    }
}

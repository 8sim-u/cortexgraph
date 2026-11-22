package com.cortexgraph.ui.physics;

import com.cortexgraph.model.GraphEdge;
import com.cortexgraph.ui.model.GraphNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GraphPhysicsEngineTest {

    private GraphPhysicsEngine engine;

    @BeforeEach
    void setUp() {
        engine = new GraphPhysicsEngine();
        engine.setCenterPoint(0.0, 0.0);
        engine.setCenterGravity(0.0); // disable center gravity for isolated physics tests
    }

    @Test
    void testAddAndRetrieveNodes() {
        GraphNode n1 = new GraphNode(1, 1, "doc1.txt", 1, "Chunk 1", 10.0, 10.0);
        engine.addNode(n1);

        assertEquals(1, engine.getNodes().size());
        assertSame(n1, engine.getNode(1));
        assertNull(engine.getNode(999));
    }

    @Test
    void testRepulsionBetweenCloseNodes() {
        GraphNode n1 = new GraphNode(1, 1, "doc1.txt", 1, "Chunk 1", -10.0, 0.0);
        GraphNode n2 = new GraphNode(2, 1, "doc1.txt", 1, "Chunk 2", 10.0, 0.0);
        engine.addNode(n1);
        engine.addNode(n2);

        double initialDistance = n1.distanceTo(n2);
        assertEquals(20.0, initialDistance, 1e-6);

        // One step of physics
        engine.step(1.0);

        double newDistance = n1.distanceTo(n2);
        assertTrue(newDistance > initialDistance, "Unconnected close nodes must repel each other");
        assertTrue(n1.getX() < -10.0);
        assertTrue(n2.getX() > 10.0);
    }

    @Test
    void testSpringAttractionBetweenDistantConnectedNodes() {
        // Rest length is 100.0, place them at distance 300.0
        engine.setRestLength(100.0);
        engine.setKSpring(0.1);
        engine.setKRepel(0.0); // disable repulsion to test pure spring attraction

        GraphNode n1 = new GraphNode(1, 1, "doc1.txt", 1, "Chunk 1", -150.0, 0.0);
        GraphNode n2 = new GraphNode(2, 1, "doc1.txt", 1, "Chunk 2", 150.0, 0.0);
        engine.addNode(n1);
        engine.addNode(n2);
        engine.addEdge(new GraphEdge(1, 2, 1.0));

        double initialDist = n1.distanceTo(n2);
        assertEquals(300.0, initialDist, 1e-6);

        engine.step(1.0);

        double newDist = n1.distanceTo(n2);
        assertTrue(newDist < initialDist, "Connected distant nodes must be pulled together by spring attraction");
    }

    @Test
    void testVelocityDampingSettlesMotion() {
        GraphNode n1 = new GraphNode(1, 1, "doc1.txt", 1, "Chunk 1", 0.0, 0.0);
        n1.setVx(50.0);
        n1.setVy(50.0);
        engine.addNode(n1);
        engine.setDamping(0.5);

        for (int i = 0; i < 20; i++) {
            engine.step(0.1);
        }

        assertTrue(Math.abs(n1.getVx()) < 0.01, "Velocity vx should damp down close to 0");
        assertTrue(Math.abs(n1.getVy()) < 0.01, "Velocity vy should damp down close to 0");
    }

    @Test
    void testFindNodeAt() {
        GraphNode n1 = new GraphNode(1, 1, "doc1.txt", 1, "Chunk 1", 100.0, 100.0);
        n1.setRadius(15.0);
        engine.addNode(n1);

        GraphNode hit = engine.findNodeAt(105.0, 105.0);
        assertNotNull(hit);
        assertEquals(1, hit.getChunkId());

        GraphNode miss = engine.findNodeAt(200.0, 200.0);
        assertNull(miss);
    }
}

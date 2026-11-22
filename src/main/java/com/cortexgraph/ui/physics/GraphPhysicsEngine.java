package com.cortexgraph.ui.physics;

import com.cortexgraph.model.GraphEdge;
import com.cortexgraph.ui.model.GraphNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2D force-directed physics engine simulating spring attraction and Coulomb node repulsion.
 */
public class GraphPhysicsEngine {
    private final Map<Integer, GraphNode> nodes = new ConcurrentHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();

    // Physics parameters
    private double kSpring = 0.05;
    private double restLength = 120.0;
    private double kRepel = 4000.0;
    private double damping = 0.85;
    private double maxVelocity = 40.0;
    private double centerGravity = 0.005;
    private double centerX = 400.0;
    private double centerY = 300.0;

    public void addNode(GraphNode node) {
        if (node != null) {
            nodes.put(node.getChunkId(), node);
        }
    }

    public void addEdge(GraphEdge edge) {
        if (edge != null) {
            edges.add(edge);
        }
    }

    public GraphNode getNode(int chunkId) {
        return nodes.get(chunkId);
    }

    public Collection<GraphNode> getNodes() {
        return nodes.values();
    }

    public List<GraphEdge> getEdges() {
        return edges;
    }

    public void clear() {
        nodes.clear();
        edges.clear();
    }

    /**
     * Executes one simulation step of delta time dt.
     */
    public synchronized void step(double dt) {
        List<GraphNode> nodeList = new ArrayList<>(nodes.values());
        int n = nodeList.size();

        // 1. Coulomb-like repulsion between all pairs of nodes
        for (int i = 0; i < n; i++) {
            GraphNode n1 = nodeList.get(i);
            for (int j = i + 1; j < n; j++) {
                GraphNode n2 = nodeList.get(j);

                double dx = n2.getX() - n1.getX();
                double dy = n2.getY() - n1.getY();
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 0.01) {
                    dx = (Math.random() - 0.5) * 2.0;
                    dy = (Math.random() - 0.5) * 2.0;
                    dist = Math.sqrt(dx * dx + dy * dy);
                }

                // Softened repulsion: F = kRepel / (dist^2 + 100)
                double fRepel = kRepel / (dist * dist + 100.0);
                double fx = (dx / dist) * fRepel;
                double fy = (dy / dist) * fRepel;

                // Repel away from each other: n1 pushed in -dx, n2 in +dx
                n1.applyForce(-fx, -fy);
                n2.applyForce(fx, fy);
            }
        }

        // 2. Hooke's Law spring attraction along edges
        for (GraphEdge edge : edges) {
            GraphNode u = nodes.get(edge.getSourceChunkId());
            GraphNode v = nodes.get(edge.getTargetChunkId());
            if (u == null || v == null) continue;

            double dx = v.getX() - u.getX();
            double dy = v.getY() - u.getY();
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < 0.01) continue;

            double displacement = dist - restLength;
            // Higher edge weight = stiffer spring
            double fSpring = kSpring * displacement * Math.max(0.1, edge.getWeight());
            double fx = (dx / dist) * fSpring;
            double fy = (dy / dist) * fSpring;

            // Pull towards each other
            u.applyForce(fx, fy);
            v.applyForce(-fx, -fy);
        }

        // 3. Center gravity (pulls drifting nodes toward canvas center)
        if (centerGravity > 0) {
            for (GraphNode node : nodeList) {
                double dx = centerX - node.getX();
                double dy = centerY - node.getY();
                node.applyForce(dx * centerGravity, dy * centerGravity);
            }
        }

        // 4. Position and velocity integration
        for (GraphNode node : nodeList) {
            node.update(dt, damping, maxVelocity);
        }
    }

    /**
     * Hit-test returning the topmost node containing point (x, y).
     */
    public GraphNode findNodeAt(double x, double y) {
        for (GraphNode node : nodes.values()) {
            if (node.contains(x, y)) {
                return node;
            }
        }
        return null;
    }

    // Setters and getters for physics parameters
    public double getKSpring() { return kSpring; }
    public void setKSpring(double kSpring) { this.kSpring = kSpring; }

    public double getRestLength() { return restLength; }
    public void setRestLength(double restLength) { this.restLength = restLength; }

    public double getKRepel() { return kRepel; }
    public void setKRepel(double kRepel) { this.kRepel = kRepel; }

    public double getDamping() { return damping; }
    public void setDamping(double damping) { this.damping = damping; }

    public double getMaxVelocity() { return maxVelocity; }
    public void setMaxVelocity(double maxVelocity) { this.maxVelocity = maxVelocity; }

    public double getCenterGravity() { return centerGravity; }
    public void setCenterGravity(double centerGravity) { this.centerGravity = centerGravity; }

    public void setCenterPoint(double x, double y) {
        this.centerX = x;
        this.centerY = y;
    }
}

package com.cortexgraph.ui.model;

/**
 * Represents a 2D physics particle node in the interactive knowledge graph canvas.
 */
public class GraphNode {
    private final int chunkId;
    private final int documentId;
    private final String documentName;
    private final int pageNumber;
    private final String content;

    private double x;
    private double y;
    private double vx;
    private double vy;
    private double fx;
    private double fy;
    private double radius = 16.0;

    private boolean pinned = false;
    private boolean highlighted = false;

    public GraphNode(int chunkId, int documentId, String documentName, int pageNumber,
                     String content, double x, double y) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.documentName = documentName;
        this.pageNumber = pageNumber;
        this.content = content;
        this.x = x;
        this.y = y;
    }

    /**
     * Accumulates an external force onto the particle.
     */
    public void applyForce(double forceX, double forceY) {
        this.fx += forceX;
        this.fy += forceY;
    }

    /**
     * Integrates velocity and position over time step dt with damping and speed clamping.
     */
    public void update(double dt, double damping, double maxVelocity) {
        if (pinned) {
            this.vx = 0.0;
            this.vy = 0.0;
            this.fx = 0.0;
            this.fy = 0.0;
            return;
        }

        this.vx += this.fx * dt;
        this.vy += this.fy * dt;

        double speed = Math.sqrt(vx * vx + vy * vy);
        if (speed > maxVelocity && speed > 0) {
            double scale = maxVelocity / speed;
            this.vx *= scale;
            this.vy *= scale;
        }

        this.x += this.vx * dt;
        this.y += this.vy * dt;

        this.vx *= damping;
        this.vy *= damping;

        this.fx = 0.0;
        this.fy = 0.0;
    }

    /**
     * Computes Euclidean distance to another graph node.
     */
    public double distanceTo(GraphNode other) {
        if (other == null) return 0.0;
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Hit-test checking if a 2D point (px, py) falls inside this node's circle.
     */
    public boolean contains(double px, double py) {
        double dx = this.x - px;
        double dy = this.y - py;
        return (dx * dx + dy * dy) <= (radius * radius);
    }

    public int getChunkId() { return chunkId; }
    public int getDocumentId() { return documentId; }
    public String getDocumentName() { return documentName; }
    public int getPageNumber() { return pageNumber; }
    public String getContent() { return content; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getVx() { return vx; }
    public void setVx(double vx) { this.vx = vx; }

    public double getVy() { return vy; }
    public void setVy(double vy) { this.vy = vy; }

    public double getRadius() { return radius; }
    public void setRadius(double radius) { this.radius = radius; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public boolean isHighlighted() { return highlighted; }
    public void setHighlighted(boolean highlighted) { this.highlighted = highlighted; }
}

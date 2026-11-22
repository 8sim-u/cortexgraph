package com.cortexgraph.ui.canvas;

import com.cortexgraph.model.GraphEdge;
import com.cortexgraph.ui.model.GraphNode;
import com.cortexgraph.ui.physics.GraphPhysicsEngine;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.Collection;

/**
 * 2D interactive canvas rendering force-directed graph nodes and edges at 60 FPS with pan and zoom.
 */
public class GraphCanvas extends Canvas {

    private static final Color BG_COLOR = Color.web("#1E1E2E");
    private static final Color EDGE_COLOR = Color.web("#45475A");
    private static final Color EDGE_HIGHLIGHT_COLOR = Color.web("#CBA6F7");
    private static final Color HIGHLIGHT_RING_COLOR = Color.web("#F38BA8");
    private static final Color TEXT_COLOR = Color.web("#CDD6F4");

    private static final Color[] DOC_PALETTE = {
            Color.web("#89B4FA"), // Blue
            Color.web("#A6E3A1"), // Green
            Color.web("#F9E2AF"), // Yellow
            Color.web("#FAB387"), // Peach
            Color.web("#94E2D5"), // Teal
            Color.web("#B4BEFE")  // Lavender
    };

    private final GraphPhysicsEngine physicsEngine;
    private NodeSelectionListener selectionListener;
    private AnimationTimer animationTimer;

    // Viewport transform
    private double panX = 0.0;
    private double panY = 0.0;
    private double zoom = 1.0;

    // Interaction state
    private GraphNode draggedNode = null;
    private GraphNode selectedNode = null;
    private double lastMouseX;
    private double lastMouseY;
    private boolean isPanning = false;

    public GraphCanvas(double width, double height) {
        this(new GraphPhysicsEngine(), width, height);
    }

    public GraphCanvas(GraphPhysicsEngine physicsEngine, double width, double height) {
        super(width, height);
        this.physicsEngine = physicsEngine;
        this.physicsEngine.setCenterPoint(width / 2.0, height / 2.0);

        setupEventHandlers();
        setupAnimationTimer();
    }

    public GraphPhysicsEngine getPhysicsEngine() {
        return physicsEngine;
    }

    public void setSelectionListener(NodeSelectionListener listener) {
        this.selectionListener = listener;
    }

    public GraphNode getSelectedNode() {
        return selectedNode;
    }

    public double screenToWorldX(double screenX) {
        return (screenX - panX) / zoom;
    }

    public double screenToWorldY(double screenY) {
        return (screenY - panY) / zoom;
    }

    public void resetView() {
        this.panX = 0.0;
        this.panY = 0.0;
        this.zoom = 1.0;
        physicsEngine.setCenterPoint(getWidth() / 2.0, getHeight() / 2.0);
    }

    private void setupEventHandlers() {
        setOnMousePressed(e -> {
            double wx = screenToWorldX(e.getX());
            double wy = screenToWorldY(e.getY());
            GraphNode hit = physicsEngine.findNodeAt(wx, wy);

            if (hit != null) {
                draggedNode = hit;
                draggedNode.setPinned(true);

                if (selectedNode != null) {
                    selectedNode.setHighlighted(false);
                }
                selectedNode = hit;
                selectedNode.setHighlighted(true);

                if (selectionListener != null) {
                    selectionListener.onNodeSelected(hit);
                }
            } else {
                isPanning = true;
            }

            lastMouseX = e.getX();
            lastMouseY = e.getY();
        });

        setOnMouseDragged(e -> {
            double dx = e.getX() - lastMouseX;
            double dy = e.getY() - lastMouseY;

            if (draggedNode != null) {
                draggedNode.setX(draggedNode.getX() + dx / zoom);
                draggedNode.setY(draggedNode.getY() + dy / zoom);
            } else if (isPanning) {
                panX += dx;
                panY += dy;
            }

            lastMouseX = e.getX();
            lastMouseY = e.getY();
        });

        setOnMouseReleased(e -> {
            if (draggedNode != null) {
                draggedNode.setPinned(false);
                draggedNode = null;
            }
            isPanning = false;
        });

        setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 0.9;
            double mouseX = e.getX();
            double mouseY = e.getY();

            panX = mouseX - (mouseX - panX) * factor;
            panY = mouseY - (mouseY - panY) * factor;
            zoom = Math.max(0.2, Math.min(4.0, zoom * factor));
        });

        widthProperty().addListener((obs, oldVal, newVal) ->
                physicsEngine.setCenterPoint(newVal.doubleValue() / 2.0, getHeight() / 2.0));
        heightProperty().addListener((obs, oldVal, newVal) ->
                physicsEngine.setCenterPoint(getWidth() / 2.0, newVal.doubleValue() / 2.0));
    }

    private void setupAnimationTimer() {
        this.animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                physicsEngine.step(0.016);
                render();
            }
        };
    }

    public void start() {
        if (animationTimer != null) {
            animationTimer.start();
        }
    }

    public void stop() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    public void render() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        // Background
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, w, h);

        gc.save();
        gc.translate(panX, panY);
        gc.scale(zoom, zoom);

        // 1. Render edges
        for (GraphEdge edge : physicsEngine.getEdges()) {
            GraphNode u = physicsEngine.getNode(edge.getSourceChunkId());
            GraphNode v = physicsEngine.getNode(edge.getTargetChunkId());
            if (u == null || v == null) continue;

            boolean edgeHighlighted = (selectedNode != null &&
                    (u.getChunkId() == selectedNode.getChunkId() || v.getChunkId() == selectedNode.getChunkId()));

            gc.setStroke(edgeHighlighted ? EDGE_HIGHLIGHT_COLOR : EDGE_COLOR);
            gc.setLineWidth(edgeHighlighted ? 2.5 : Math.max(1.0, edge.getWeight() * 2.5));
            gc.strokeLine(u.getX(), u.getY(), v.getX(), v.getY());
        }

        // 2. Render nodes
        Collection<GraphNode> nodes = physicsEngine.getNodes();
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Menlo", 10));

        for (GraphNode node : nodes) {
            double r = node.getRadius();
            double x = node.getX() - r;
            double y = node.getY() - r;

            // Highlight ring
            if (node.isHighlighted()) {
                gc.setStroke(HIGHLIGHT_RING_COLOR);
                gc.setLineWidth(3.5);
                gc.strokeOval(x - 4, y - 4, (r + 4) * 2, (r + 4) * 2);
            }

            // Node body
            int colorIdx = Math.floorMod(node.getDocumentId(), DOC_PALETTE.length);
            gc.setFill(DOC_PALETTE[colorIdx]);
            gc.fillOval(x, y, r * 2, r * 2);

            gc.setStroke(Color.web("#11111B"));
            gc.setLineWidth(1.5);
            gc.strokeOval(x, y, r * 2, r * 2);

            // Chunk label
            gc.setFill(Color.web("#11111B"));
            gc.fillText("C" + node.getChunkId(), node.getX(), node.getY() + 3);
        }

        gc.restore();
    }
}

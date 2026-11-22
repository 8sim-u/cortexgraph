package com.cortexgraph.ui.canvas;

import com.cortexgraph.ui.model.GraphNode;

/**
 * Listener notified when a graph node is selected in the canvas.
 */
@FunctionalInterface
public interface NodeSelectionListener {
    void onNodeSelected(GraphNode node);
}

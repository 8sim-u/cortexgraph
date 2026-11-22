# CortexGraph Step 4: JavaFX UI, 2D Physics Graph Canvas & RAG Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

---

## Goal Description

Build **Step 4** of CortexGraph in `com.cortexgraph.ui` (with supporting `RagChatService` in `com.cortexgraph.rag`). This provides an interactive, modern dark-themed desktop application with a 60-FPS physics-based 2D force-directed knowledge graph, node dragging/zooming/panning, node selection inspection (document name, page number, chunk text), document ingestion, and a grounded RAG chat interface with citations.

---

## User Review Required

> [!IMPORTANT]
> - **Physics & UI Architecture Decoupling**: Physics calculations (`GraphNode`, `GraphPhysicsEngine`) are decoupled from JavaFX Canvas rendering (`GraphCanvas`). This enables 100% headless automated unit testing in Maven without needing a graphical display.
> - **Threading Model**: Document ingestion and RAG query processing run off the JavaFX Application Thread via `CompletableFuture` / background executor, dispatching UI updates safely via `Platform.runLater`.
> - **Offline Deterministic Fallback**: `RagChatService` formats grounded answers citing `[Filename, Page X]` using the deterministic local embedding fallback without requiring an external API key or running Ollama daemon.

---

## Proposed Changes

### Component 1: Physics Engine & UI Models (`com.cortexgraph.ui.model`, `com.cortexgraph.ui.physics`)
- `GraphNode`: Encapsulates 2D position $(x, y)$, velocity $(vx, vy)$, radius, chunk metadata (document name, page number, text), pinned state, and highlight state.
- `GraphPhysicsEngine`: Implements spring attraction (Hooke's Law), node-node repulsion (Coulomb-like force), center gravitational pull, velocity damping, and hit-testing.

### Component 2: RAG Grounded Chat Service (`com.cortexgraph.rag`)
- `RagChatService`: Executes query embedding, top-K nearest chunk retrieval via `VectorSearchService`, extracts metadata (`DocumentRecord`, page number), formats grounded response with `[Filename, Page X]` citations, and logs interaction in SQLite via `ChatDAO`.

### Component 3: 2D Physics Canvas & Event Handling (`com.cortexgraph.ui.canvas`)
- `GraphCanvas`: Custom JavaFX Canvas with `AnimationTimer` running a 60-FPS physics & render loop. Supports:
  - Dragging nodes with mouse (sets node `pinned = true`).
  - Canvas panning with mouse drag on empty space.
  - Zooming in/out with scroll wheel.
  - Node click selection: highlights the clicked node and notifies listeners.

### Component 4: Main Application & Dark Modern UI (`com.cortexgraph.ui`)
- `CortexGraphApp`: Modern dark-palette layout (`#1E1E2E` canvas background, `#181825` sidebar, `#89B4FA` node accents, `#A6E3A1` citations):
  - Top header: Ingest Document button (`FileChooser` $\to$ async `DocumentIngestor`), Graph Edge Threshold slider, Reset/Layout buttons.
  - Center: `GraphCanvas` in a responsive pane.
  - Right sidebar:
    - Top: Ingested documents list + Selected chunk/page source viewer.
    - Bottom: Q&A chat history + prompt text field + Send button.
- `pom.xml`: Add `javafx-maven-plugin` configuration for direct execution (`mvn javafx:run`).

---

## Detailed Task Breakdown

### Task 1: UI Domain Models (`GraphNode`)
**Files:**
- Create: `src/main/java/com/cortexgraph/ui/model/GraphNode.java`
- Test: `src/test/java/com/cortexgraph/ui/model/GraphNodeTest.java`

- [ ] **Step 1: Write the failing test**
  - Verify node position initialization, velocity update, force accumulation, distance calculation, hit testing, and pinning.
- [ ] **Step 2: Run test to verify it fails (`mvn test -Dtest=GraphNodeTest`)**
- [ ] **Step 3: Implement `GraphNode`**
  - Properties: `chunkId`, `documentId`, `documentName`, `pageNumber`, `content`, `x`, `y`, `vx`, `vy`, `radius`, `pinned`, `highlighted`.
  - Methods: `applyForce(fx, fy)`, `update(dt, damping, maxVelocity)`, `distanceTo(other)`, `contains(px, py)`.
- [ ] **Step 4: Run test to verify it passes (`mvn test -Dtest=GraphNodeTest`)**
- [ ] **Step 5: Commit (`feat: implement GraphNode model with 2D physics properties`)**

---

### Task 2: 2D Force-Directed Physics Engine (`GraphPhysicsEngine`)
**Files:**
- Create: `src/main/java/com/cortexgraph/ui/physics/GraphPhysicsEngine.java`
- Test: `src/test/java/com/cortexgraph/ui/physics/GraphPhysicsEngineTest.java`

- [ ] **Step 1: Write the failing test**
  - Verify spring attraction between connected nodes (`GraphEdge`).
  - Verify repulsion between overlapping or adjacent nodes.
  - Verify center gravity and velocity damping over time.
  - Verify hit-testing (`findNodeAt`).
- [ ] **Step 2: Run test to verify it fails (`mvn test -Dtest=GraphPhysicsEngineTest`)**
- [ ] **Step 3: Implement `GraphPhysicsEngine`**
  - Parameters: `kSpring`, `restLength`, `kRepel`, `damping`, `centerGravity`.
  - Methods: `addNode(GraphNode)`, `addEdge(GraphEdge)`, `clear()`, `step(double dt)`, `findNodeAt(double x, double y)`.
- [ ] **Step 4: Run test to verify it passes (`mvn test -Dtest=GraphPhysicsEngineTest`)**
- [ ] **Step 5: Commit (`feat: implement GraphPhysicsEngine with spring attraction and repulsion`)**

---

### Task 3: Grounded RAG Chat Service (`RagChatService`)
**Files:**
- Create: `src/main/java/com/cortexgraph/rag/RagChatService.java`
- Test: `src/test/java/com/cortexgraph/rag/RagChatServiceTest.java`

- [ ] **Step 1: Write the failing test**
  - Verify top-K retrieval given a user question.
  - Verify grounded citation formatting `[Filename, Page X]`.
  - Verify SQLite persistence in `ChatDAO`.
  - Verify fallback behavior when no documents or chunks are available.
- [ ] **Step 2: Run test to verify it fails (`mvn test -Dtest=RagChatServiceTest`)**
- [ ] **Step 3: Implement `RagChatService`**
  - Injects `VectorSearchService`, `EmbeddingService`, `ChunkDAO`, `DocumentDAO`, `ChatDAO`.
  - Formats grounded answers citing source document name and page number.
- [ ] **Step 4: Run test to verify it passes (`mvn test -Dtest=RagChatServiceTest`)**
- [ ] **Step 5: Commit (`feat: implement RagChatService with top-K search and grounded citations`)**

---

### Task 4: Interactive 2D Physics Canvas (`GraphCanvas`)
**Files:**
- Create: `src/main/java/com/cortexgraph/ui/canvas/GraphCanvas.java`
- Create: `src/main/java/com/cortexgraph/ui/canvas/NodeSelectionListener.java`

- [ ] **Step 1: Implement `NodeSelectionListener` functional interface**
  - `void onNodeSelected(GraphNode node);`
- [ ] **Step 2: Implement `GraphCanvas`**
  - Extends `javafx.scene.canvas.Canvas`.
  - Integrates `GraphPhysicsEngine` and 60-FPS `AnimationTimer`.
  - Implements mouse event handlers:
    - Drag individual node (`node.setPinned(true)` during drag, unpin on release).
    - Pan canvas background (`panX, panY`).
    - Scroll to zoom (`zoomFactor`).
    - Click node to highlight and trigger `NodeSelectionListener`.
  - Renders glowing nodes, edge lines with weight-proportional opacity/stroke, and document-colored nodes.
- [ ] **Step 3: Compile check (`mvn test-compile`)**
- [ ] **Step 4: Commit (`feat: implement GraphCanvas with 60-FPS AnimationTimer and mouse interaction`)**

---

### Task 5: JavaFX Application & Dark-Mode Layout (`CortexGraphApp`)
**Files:**
- Create: `src/main/java/com/cortexgraph/ui/CortexGraphApp.java`
- Modify: `pom.xml` (add `javafx-maven-plugin` configuration)

- [ ] **Step 1: Configure `pom.xml` with `javafx-maven-plugin`**
  - Allows running `mvn javafx:run` cleanly from terminal.
- [ ] **Step 2: Implement `CortexGraphApp`**
  - Modern dark-palette layout:
    - Top Bar: Ingest Document button, Edge Similarity Threshold slider, Re-layout button.
    - Left/Center: `GraphCanvas` with zoom/pan and physics visualization.
    - Right Top: Document list + Selected chunk/page source viewer (showing Document Name, Page Number, and Content).
    - Right Bottom: Chat history view + question input + Send button.
  - Background async integration with `DocumentIngestor` and `RagChatService` via `CompletableFuture` and `Platform.runLater`.
- [ ] **Step 3: Compile and test verification (`mvn clean test`)**
- [ ] **Step 4: Commit (`feat: implement CortexGraphApp dark-mode UI with split sidebar and chat`)**

---

### Task 6: End-to-End UI & RAG Integration Test
**Files:**
- Create: `src/test/java/com/cortexgraph/ui/CortexGraphAppIntegrationTest.java`

- [ ] **Step 1: Write integration test**
  - Test ingestion of documents $\to$ physics graph node/edge generation $\to$ RAG question answering with citations.
- [ ] **Step 2: Run all tests in the project (`mvn clean test`)**
- [ ] **Step 3: Commit (`test: add end-to-end integration test for physics graph and RAG chat workflow`)**

---

## Verification Plan

### Automated Tests
```bash
mvn clean test
```
Ensures all 56 existing unit/integration tests and newly added Step 4 tests pass with 0 errors.

### Manual Verification
```bash
mvn javafx:run
```
1. Verify application launches in dark mode (`#1E1E2E`).
2. Click "Ingest Document" and select sample PDF/TXT file; verify nodes populate canvas.
3. Observe physics animation (spring attraction and repulsion settling smoothly).
4. Drag a node with mouse; verify it moves smoothly.
5. Pan canvas by dragging background; scroll mouse wheel to zoom in/out.
6. Click a node; verify top-right pane displays Document Name, Page Number, and Chunk Content.
7. Enter a question in the chat box and click "Send"; verify assistant response arrives with `[Filename, Page X]` citations.

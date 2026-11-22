# CortexGraph Step 5: Final Wiring, Persistence Verification & Smoke Check Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

---

## Goal Description

Build **Step 5** (final wiring, persistence verification, and smoke check) for CortexGraph:
1. Verify and complete end-to-end persistence in `CortexGraphApp`:
   - Automatically load ingested documents, chunks, SQLite binary embeddings, similarity edges, and chat history on startup.
   - Refactor ingestion logic into a shared, robust `ingestFile(File)` method supporting both the `FileChooser` button and seamless Drag-and-Drop directly onto the canvas/window.
2. Ensure bulletproof concurrency lifecycle:
   - Provide a dedicated managed `backgroundExecutor` using named daemon threads (`CortexGraph-Worker`) so background tasks never block JVM exit.
   - In `Application.stop()`, stop `AnimationTimer`, close `DocumentIngestor`, and terminate `backgroundExecutor`.
3. Add a comprehensive end-to-end restart integration test (`PersistenceRestartIntegrationTest`) proving that all documents, chunks, vectors, similarity graph edges, and chat history survive complete application/service shutdown and restore seamlessly on restart.
4. Run full Maven test suite (`mvn clean test`) and package smoke test (`mvn package`).

---

## User Review Required

> [!IMPORTANT]
> - **Zero Re-computation on Restart**: When CortexGraph starts up, previously computed 64-dimensional chunk embeddings are restored directly from SQLite BLOBs into the `EmbeddingService` cache in milliseconds, avoiding redundant embedding calls or network traffic.
> - **Daemon Thread Pool**: Background ingestion and RAG querying use a daemon thread executor (`CortexGraph-Worker`), ensuring clean application exit without hanging processes.
> - **Dual Ingestion**: Users can ingest documents either via the "📂 Ingest Document" button or by dragging and dropping `.pdf`, `.txt`, and `.md` files directly onto the window.

---

## Proposed Changes

### Component 1: Application Wiring & Drag-and-Drop Ingestion (`com.cortexgraph.ui.CortexGraphApp`)
- [MODIFY] `src/main/java/com/cortexgraph/ui/CortexGraphApp.java`:
  - Add Drag-and-Drop file handling on `root` (`setOnDragOver`, `setOnDragDropped`).
  - Extract `ingestFile(File file)` as a shared async method used by both `FileChooser` and Drag-and-Drop.
  - Introduce `backgroundExecutor` with named daemon threads.
  - Complete `stop()` with clean executor shutdown.

### Component 2: Persistence & Restart Integration Test (`com.cortexgraph.PersistenceRestartIntegrationTest`)
- [NEW] `src/test/java/com/cortexgraph/PersistenceRestartIntegrationTest.java`:
  - Ingests multiple files, generates embeddings, builds graph edges, and logs chat interactions.
  - Simulates complete shutdown by disposing all services and closing connections.
  - Starts fresh session pointing to the same SQLite database URL.
  - Asserts that all documents, chunks, embeddings (without re-computation), graph edges, and chat messages are completely restored and functional.

---

## Detailed Task Breakdown

### Task 1: Refactor Ingestion, Drag-and-Drop & Managed Daemon Executor (`CortexGraphApp`)
**Files:**
- Modify: `src/main/java/com/cortexgraph/ui/CortexGraphApp.java`

- [ ] **Step 1: Update `CortexGraphApp.java`**
  - Add `ExecutorService backgroundExecutor = Executors.newFixedThreadPool(..., daemonFactory)`.
  - Add Drag-and-Drop handlers to `root`:
    ```java
    root.setOnDragOver(event -> {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    });
    root.setOnDragDropped(event -> {
        Dragboard db = event.getDragboard();
        if (db.hasFiles()) {
            for (File file : db.getFiles()) {
                ingestFile(file);
            }
            event.setDropCompleted(true);
        }
        event.consume();
    });
    ```
  - Extract `public CompletableFuture<Void> ingestFile(File file)` ensuring UI disable/enable, async pipeline, database persistence, and `Platform.runLater` updates.
  - Update `stop()`:
    ```java
    @Override
    public void stop() throws Exception {
        if (graphCanvas != null) graphCanvas.stop();
        if (ingestor != null) ingestor.close();
        if (backgroundExecutor != null) backgroundExecutor.shutdownNow();
        super.stop();
    }
    ```
- [ ] **Step 2: Run test compilation (`mvn test-compile`)**
- [ ] **Step 3: Commit (`feat: add drag-and-drop ingestion and daemon executor with clean shutdown in CortexGraphApp`)**

---

### Task 2: Restart Persistence Integration Test (`PersistenceRestartIntegrationTest`)
**Files:**
- Create: `src/test/java/com/cortexgraph/PersistenceRestartIntegrationTest.java`

- [ ] **Step 1: Write `PersistenceRestartIntegrationTest`**
  - Session 1:
    - Save documents (`report.txt`, `notes.md`).
    - Parse, chunk, and embed.
    - Persist embeddings to SQLite.
    - Ask a question via `RagChatService`, verify grounded response and saved chat history.
    - Clear memory / close services.
  - Session 2:
    - Re-open DAOs pointing to the same SQLite file.
    - Instantiate fresh `EmbeddingService` (empty cache).
    - Call `loadFromDatabase` and verify cache count matches.
    - Rebuild physics graph from restored records and cached embeddings without re-embedding.
    - Verify chat history loaded accurately.
    - Ask a follow-up query and verify correct citations.
- [ ] **Step 2: Run test (`mvn test -Dtest=PersistenceRestartIntegrationTest`)**
- [ ] **Step 3: Commit (`test: add persistence restart integration test verifying full state restoration`)**

---

### Task 3: Full Project Test Suite, Packaging & Smoke Check
- [ ] **Step 1: Run full test suite (`mvn clean test`)**
  - Verify all 70+ tests pass with 0 failures.
- [ ] **Step 2: Run Maven packaging check (`mvn package -DskipTests=false`)**
  - Verify executable artifact creation.
- [ ] **Step 3: Commit & update documentation**

---

## Verification Plan

### Automated Tests
```bash
mvn clean test
```
Ensures 100% of unit, integration, and restart persistence tests pass.

### Packaging Check
```bash
mvn package
```
Ensures standard Maven build produces `target/cortexgraph-1.0.0-SNAPSHOT.jar`.

### Interactive Smoke Test
```bash
mvn javafx:run
```
- App launches with dark theme.
- Drag-and-drop a text file or PDF onto the window; verify nodes appear on canvas.
- Close app, reopen with `mvn javafx:run`; verify ingested documents, nodes, edges, and chat history load immediately.

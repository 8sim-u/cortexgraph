# CortexGraph Step 3: Embeddings, Vector Search & Knowledge Graph Edges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Step 3 in `com.cortexgraph.rag` (with supporting model and DAO updates) to generate embeddings using LangChain4j with a deterministic local fallback, cache vectors in a `ConcurrentHashMap`, persist/load embeddings in SQLite, perform Top-K vector retrieval using a `PriorityQueue`, and construct similarity `GraphEdge`s between related chunks.

**Architecture:** A mathematical utility (`VectorUtils`) provides safe cosine similarity and IEEE 754 `ByteBuffer` binary serialization. A LangChain4j-compatible `DeterministicEmbeddingModel` guarantees zero-dependency local operation alongside `EmbeddingService`, which caches vectors in memory (`ConcurrentHashMap<String, double[]>`) and syncs with SQLite via `EmbeddingDAO`. A `VectorSearchService` implements an $O(N \log k)$ `PriorityQueue` min-heap for Top-K retrieval and generates filtered `GraphEdge` relationships above a configurable similarity threshold.

**Tech Stack:** Java 21 (`java.nio.ByteBuffer`, `PriorityQueue`, `ConcurrentHashMap`), LangChain4j 0.35.0 (`EmbeddingModel`, `Embedding`, `Response`, `TextSegment`), SQLite JDBC 3.45.2.0, JUnit Jupiter 5.10.2.

**Spec:** Direct user request for Step 3: `EmbeddingService` using LangChain4j with Gemini/Ollama support + deterministic local fallback embedder; `ConcurrentHashMap<String, double[]>` vector cache with SQLite persistence/loading; safe `cosineSimilarity` and `topKChunks` via `PriorityQueue`; `GraphEdge` model and similarity edge generation with configurable threshold (e.g. 0.80); and comprehensive unit/integration tests.

---

## Global Constraints

- Java version: 21 (using `ByteBuffer`, `DoubleBuffer`, `PriorityQueue`, `ConcurrentHashMap`).
- Packaging: RAG services in `com.cortexgraph.rag`, models in `com.cortexgraph.model`, DAOs in `com.cortexgraph.dao`.
- LangChain4j integration: `DeterministicEmbeddingModel` must implement `dev.langchain4j.model.embedding.EmbeddingModel` so it can plug into any LangChain4j pipeline.
- SQLite persistence: vectors are stored in SQLite as binary `BLOB`s via `ByteBuffer` for performance and zero precision loss.
- Zero external dependencies required for tests: tests and offline demos must never require external API keys or a running Ollama daemon.
- Compatibility: all existing Step 1 & Step 2 tests (database, parsers, chunker, ingestor) must remain 100% green.

---

## Review Focus

1. **Division by Zero & Dimension Mismatch in Cosine Similarity**: Zero vectors (norm = 0), null arrays, and vectors of unequal dimensions must return `0.0` safely without throwing exceptions.
2. **Binary Vector Serialization Precision**: Serializing `double[]` to `byte[]` and deserializing back must preserve exact 64-bit IEEE 754 values.
3. **PriorityQueue Top-K Boundary Cases**: When candidate count $N \le k$, all $N$ chunks must be returned in descending similarity order; when $k \le 0$, an empty list is returned.
4. **Graph Edge Graph-Sanity**: Similarity edges must be undirected/unique (only pairs $i < j$, no reverse duplicate edges) and must never create self-loops ($i = i$).
5. **Database Cache Synchronization**: Chunks with persisted embeddings in SQLite must load directly into the `ConcurrentHashMap` cache without recomputation.

---

### Task 1: Mathematical & Vector Utilities (`VectorUtils`)

**Files:**
- Create: `src/main/java/com/cortexgraph/rag/VectorUtils.java`
- Test: `src/test/java/com/cortexgraph/rag/VectorUtilsTest.java`

**Interfaces:**
- Produces:
  - `public static double cosineSimilarity(double[] a, double[] b)`
  - `public static byte[] toByteArray(double[] vector)`
  - `public static double[] fromByteArray(byte[] bytes)`
  - `public static double[] normalize(double[] vector)`

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph.rag;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VectorUtilsTest {
    @Test
    void testCosineSimilarityIdenticalAndOrthogonal() {
        double[] v1 = {1.0, 0.0, 0.0};
        double[] v2 = {1.0, 0.0, 0.0};
        double[] v3 = {0.0, 1.0, 0.0};

        assertEquals(1.0, VectorUtils.cosineSimilarity(v1, v2), 1e-6);
        assertEquals(0.0, VectorUtils.cosineSimilarity(v1, v3), 1e-6);
    }

    @Test
    void testCosineSimilaritySafeEdgeCases() {
        double[] v1 = {1.0, 2.0};
        double[] zero = {0.0, 0.0};
        double[] diffDim = {1.0, 2.0, 3.0};

        assertEquals(0.0, VectorUtils.cosineSimilarity(v1, zero));
        assertEquals(0.0, VectorUtils.cosineSimilarity(v1, diffDim));
        assertEquals(0.0, VectorUtils.cosineSimilarity(v1, null));
        assertEquals(0.0, VectorUtils.cosineSimilarity(null, null));
    }

    @Test
    void testBinarySerializationFidelity() {
        double[] original = {0.123456789, -0.987654321, 3.1415926535};
        byte[] bytes = VectorUtils.toByteArray(original);
        assertNotNull(bytes);
        assertEquals(original.length * Double.BYTES, bytes.length);

        double[] restored = VectorUtils.fromByteArray(bytes);
        assertArrayEquals(original, restored, 1e-15);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=VectorUtilsTest`
Expected: FAIL (cannot find symbol `VectorUtils`)

- [ ] **Step 3: Implement `VectorUtils`**

- Implement `cosineSimilarity`: compute dot product and Euclidean norms; guard against zero norms, null, and mismatched lengths.
- Implement `toByteArray` and `fromByteArray` using `ByteBuffer.allocate(vector.length * Double.BYTES)`.
- Implement `normalize(double[] vector)`: scale vector so Euclidean norm = 1.0.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=VectorUtilsTest`
Expected: Tests run: 3, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/rag/VectorUtils.java src/test/java/com/cortexgraph/rag/VectorUtilsTest.java
git commit -m "feat: implement VectorUtils with safe cosine similarity and byte buffer serialization"
```

---

### Task 2: Domain Models (`ScoredChunk` & `GraphEdge`)

**Files:**
- Create: `src/main/java/com/cortexgraph/model/ScoredChunk.java`
- Create: `src/main/java/com/cortexgraph/model/GraphEdge.java`
- Test: `src/test/java/com/cortexgraph/model/GraphEdgeTest.java`

**Interfaces:**
- Produces:
  - `ScoredChunk(TextChunk chunk, double score)`
  - `GraphEdge(int sourceChunkId, int targetChunkId, double weight, String relationType)` + default relation `"SIMILAR_TO"`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=GraphEdgeTest`
Expected: FAIL (cannot find symbol `GraphEdge`, `ScoredChunk`)

- [ ] **Step 3: Implement `ScoredChunk` and `GraphEdge`**

- Create `ScoredChunk(TextChunk chunk, double score)` with getters and `toString()`.
- Create `GraphEdge(int sourceChunkId, int targetChunkId, double weight, String relationType)` with overloaded constructor defaulting to `"SIMILAR_TO"`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=GraphEdgeTest`
Expected: Tests run: 2, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/model/ src/test/java/com/cortexgraph/model/GraphEdgeTest.java
git commit -m "feat: add ScoredChunk and GraphEdge domain models"
```

---

### Task 3: SQLite Embeddings Persistence (`EmbeddingDAO` & Schema)

**Files:**
- Modify: `src/main/java/com/cortexgraph/dao/DatabaseManager.java`
- Create: `src/main/java/com/cortexgraph/dao/EmbeddingDAO.java`
- Test: `src/test/java/com/cortexgraph/dao/EmbeddingDAOTest.java`

**Interfaces:**
- Consumes: `DatabaseManager`, `VaultException`, `VectorUtils`
- Produces:
  - `DatabaseManager` creates table `embeddings (chunk_id INTEGER PRIMARY KEY, vector BLOB NOT NULL, ...)`
  - `EmbeddingDAO`: `save(int chunkId, double[] vector)`, `saveAll(Map<Integer, double[]> embeddings)`, `findByChunkId(int chunkId)`, `findAll()`, `deleteByChunkId(int chunkId)`

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph.dao;

import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class EmbeddingDAOTest {
    private EmbeddingDAO embeddingDAO;
    private ChunkDAO chunkDAO;
    private DocumentDAO docDAO;
    private int chunkId;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("embed_test.db");
        DatabaseManager.initializeDatabase(dbUrl);
        docDAO = new DocumentDAO(dbUrl);
        chunkDAO = new ChunkDAO(dbUrl);
        embeddingDAO = new EmbeddingDAO(dbUrl);

        int docId = docDAO.save(new DocumentRecord("test.txt", "/test.txt", 100L));
        chunkId = chunkDAO.save(new TextChunk(docId, 0, "Chunk text"));
    }

    @Test
    void testSaveAndFindEmbedding() throws Exception {
        double[] vec = {0.1, 0.5, -0.2};
        embeddingDAO.save(chunkId, vec);

        Optional<double[]> fetched = embeddingDAO.findByChunkId(chunkId);
        assertTrue(fetched.isPresent());
        assertArrayEquals(vec, fetched.get(), 1e-6);

        Map<Integer, double[]> all = embeddingDAO.findAll();
        assertEquals(1, all.size());
        assertTrue(all.containsKey(chunkId));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=EmbeddingDAOTest`
Expected: FAIL (cannot find symbol `EmbeddingDAO`)

- [ ] **Step 3: Update `DatabaseManager` and implement `EmbeddingDAO`**

- In `DatabaseManager.initializeDatabase`, add `CREATE TABLE IF NOT EXISTS embeddings (chunk_id INTEGER PRIMARY KEY, vector BLOB NOT NULL, FOREIGN KEY (chunk_id) REFERENCES chunks(id) ON DELETE CASCADE);`.
- Implement `EmbeddingDAO` with `save`, `saveAll` (batch transaction), `findByChunkId`, `findAll`, and `deleteByChunkId` using `VectorUtils.toByteArray` and `VectorUtils.fromByteArray`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=EmbeddingDAOTest`
Expected: Tests run: 1, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/dao/ src/test/java/com/cortexgraph/dao/EmbeddingDAOTest.java
git commit -m "feat: implement EmbeddingDAO and SQLite embeddings table schema"
```

---

### Task 4: LangChain4j Embedding Integration (`DeterministicEmbeddingModel` & `EmbeddingService`)

**Files:**
- Create: `src/main/java/com/cortexgraph/rag/DeterministicEmbeddingModel.java`
- Create: `src/main/java/com/cortexgraph/rag/EmbeddingService.java`
- Test: `src/test/java/com/cortexgraph/rag/EmbeddingServiceTest.java`

**Interfaces:**
- Consumes: LangChain4j `EmbeddingModel`, `VectorUtils`, `EmbeddingDAO`
- Produces:
  - `DeterministicEmbeddingModel implements EmbeddingModel`: 128-dimensional deterministic L2-normalized vectors.
  - `EmbeddingService`: manages `ConcurrentHashMap<String, double[]>` vector cache, delegates to model with fallback, and supports SQLite persistence/loading.

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph.rag;

import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EmbeddingServiceTest {
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(new DeterministicEmbeddingModel(64));
    }

    @Test
    void testDeterministicEmbeddingsConsistency() {
        double[] v1 = embeddingService.embedText("Knowledge graphs and retrieval augmented generation");
        double[] v2 = embeddingService.embedText("Knowledge graphs and retrieval augmented generation");
        double[] v3 = embeddingService.embedText("Unrelated cooking recipe");

        assertEquals(64, v1.length);
        assertArrayEquals(v1, v2, 1e-9);

        // Normalized: norm must be 1.0
        double norm = 0.0;
        for (double d : v1) norm += d * d;
        assertEquals(1.0, Math.sqrt(norm), 1e-6);

        // Similar text should have higher cosine similarity than unrelated text
        double simSelf = VectorUtils.cosineSimilarity(v1, v2);
        double simDiff = VectorUtils.cosineSimilarity(v1, v3);
        assertEquals(1.0, simSelf, 1e-6);
        assertTrue(simSelf > simDiff);
    }

    @Test
    void testVectorCache() {
        TextChunk chunk = new TextChunk(42, 1, 0, "Cached text content");
        double[] v1 = embeddingService.embedChunk(chunk);
        assertNotNull(v1);

        double[] cached = embeddingService.getCachedEmbedding(String.valueOf(chunk.getId()));
        assertNotNull(cached);
        assertSame(v1, cached);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=EmbeddingServiceTest`
Expected: FAIL (cannot find symbol `DeterministicEmbeddingModel`, `EmbeddingService`)

- [ ] **Step 3: Implement `DeterministicEmbeddingModel` and `EmbeddingService`**

- `DeterministicEmbeddingModel(int dimension)`: implements `dev.langchain4j.model.embedding.EmbeddingModel`. Tokenizes text, hashes tokens to dimension slots, and applies L2 normalization.
- `EmbeddingService`:
  - `ConcurrentHashMap<String, double[]> cache = new ConcurrentHashMap<>()`.
  - Constructors: default (uses local deterministic fallback or env-based remote model), or custom `EmbeddingModel`.
  - `double[] embedText(String text)`
  - `double[] embedChunk(TextChunk chunk)` (caches under `String.valueOf(chunk.getId())`).
  - `loadFromDatabase(EmbeddingDAO dao)` & `persistToDatabase(EmbeddingDAO dao)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=EmbeddingServiceTest`
Expected: Tests run: 2, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/rag/DeterministicEmbeddingModel.java src/main/java/com/cortexgraph/rag/EmbeddingService.java src/test/java/com/cortexgraph/rag/EmbeddingServiceTest.java
git commit -m "feat: implement DeterministicEmbeddingModel and EmbeddingService with ConcurrentHashMap cache"
```

---

### Task 5: Vector Search & Similarity Graph (`VectorSearchService`)

**Files:**
- Create: `src/main/java/com/cortexgraph/rag/VectorSearchService.java`
- Test: `src/test/java/com/cortexgraph/rag/VectorSearchServiceTest.java`

**Interfaces:**
- Consumes: `TextChunk`, `ScoredChunk`, `GraphEdge`, `VectorUtils`, `EmbeddingService`
- Produces:
  - `List<ScoredChunk> topKChunks(double[] queryVector, List<TextChunk> candidateChunks, EmbeddingService embeddingService, int k)`
  - `List<GraphEdge> buildSimilarityEdges(List<TextChunk> chunks, EmbeddingService embeddingService, double threshold)`

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph.rag;

import com.cortexgraph.model.GraphEdge;
import com.cortexgraph.model.ScoredChunk;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VectorSearchServiceTest {
    private VectorSearchService searchService;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(new DeterministicEmbeddingModel(32));
        searchService = new VectorSearchService();
    }

    @Test
    void testTopKChunksOrdering() {
        TextChunk c1 = new TextChunk(1, 1, 0, "Artificial intelligence and machine learning neural networks");
        TextChunk c2 = new TextChunk(2, 1, 1, "Deep learning algorithms for computer vision and neural networks");
        TextChunk c3 = new TextChunk(3, 1, 2, "Baking bread with yeast flour water and sugar in the kitchen");

        List<TextChunk> candidates = List.of(c1, c2, c3);
        double[] queryVec = embeddingService.embedText("machine learning and neural networks");

        List<ScoredChunk> top2 = searchService.topKChunks(queryVec, candidates, embeddingService, 2);

        assertEquals(2, top2.size());
        assertTrue(top2.get(0).getScore() >= top2.get(1).getScore());
        // Top 2 should be AI chunks, not bread baking
        assertTrue(top2.get(0).getChunk().getId() == 1 || top2.get(0).getChunk().getId() == 2);
    }

    @Test
    void testBuildSimilarityEdgesThreshold() {
        TextChunk c1 = new TextChunk(1, 1, 0, "Artificial intelligence and machine learning algorithms");
        TextChunk c2 = new TextChunk(2, 1, 1, "Artificial intelligence and machine learning algorithms"); // identical -> sim 1.0
        TextChunk c3 = new TextChunk(3, 1, 2, "Completely different topic gardening gardening");

        List<GraphEdge> edges = searchService.buildSimilarityEdges(List.of(c1, c2, c3), embeddingService, 0.80);

        assertEquals(1, edges.size());
        assertEquals(1, edges.get(0).getSourceChunkId());
        assertEquals(2, edges.get(0).getTargetChunkId());
        assertTrue(edges.get(0).getWeight() >= 0.80);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=VectorSearchServiceTest`
Expected: FAIL (cannot find symbol `VectorSearchService`)

- [ ] **Step 3: Implement `VectorSearchService`**

- `topKChunks`:
  - If $k \le 0$ or candidates is empty, return empty list.
  - Min-heap `PriorityQueue<ScoredChunk>` ordered by `Comparator.comparingDouble(ScoredChunk::getScore)`.
  - Bounded size $k$: maintain lowest score at the top, evict when a higher score is found.
  - Sort results descending before returning.
- `buildSimilarityEdges`:
  - Double loop for $i < j$, compute `VectorUtils.cosineSimilarity(vI, vJ)`.
  - Filter `score >= threshold` and return `List<GraphEdge>`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=VectorSearchServiceTest`
Expected: Tests run: 2, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/rag/VectorSearchService.java src/test/java/com/cortexgraph/rag/VectorSearchServiceTest.java
git commit -m "feat: implement VectorSearchService with PriorityQueue top-K and graph edge creation"
```

---

### Task 6: Step 3 End-to-End Integration Test & Full Suite Run

**Files:**
- Create: `src/test/java/com/cortexgraph/rag/RAGIntegrationTest.java`

- [ ] **Step 1: Write end-to-end integration test verifying ingestion -> embedding -> SQLite cache -> top-K search -> similarity graph**

```java
package com.cortexgraph.rag;

import com.cortexgraph.dao.ChunkDAO;
import com.cortexgraph.dao.DatabaseManager;
import com.cortexgraph.dao.DocumentDAO;
import com.cortexgraph.dao.EmbeddingDAO;
import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.GraphEdge;
import com.cortexgraph.model.ScoredChunk;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RAGIntegrationTest {
    @Test
    void testFullRAGPipelineWithDatabaseAndGraph(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("rag_e2e.db");
        DatabaseManager.initializeDatabase(dbUrl);

        DocumentDAO docDAO = new DocumentDAO(dbUrl);
        ChunkDAO chunkDAO = new ChunkDAO(dbUrl);
        EmbeddingDAO embeddingDAO = new EmbeddingDAO(dbUrl);

        int docId = docDAO.save(new DocumentRecord("ai_paper.txt", "/ai_paper.txt", 1000L));
        int c1Id = chunkDAO.save(new TextChunk(docId, 0, "Graph neural networks for knowledge discovery in deep learning"));
        int c2Id = chunkDAO.save(new TextChunk(docId, 1, "Neural network graph representations and knowledge graphs"));
        int c3Id = chunkDAO.save(new TextChunk(docId, 2, "Baking chocolate chip cookies in an oven"));

        EmbeddingService embeddingService = new EmbeddingService(new DeterministicEmbeddingModel(64));
        VectorSearchService searchService = new VectorSearchService();

        // 1. Embed and persist
        List<TextChunk> chunks = chunkDAO.findByDocumentId(docId);
        for (TextChunk chunk : chunks) {
            embeddingService.embedChunk(chunk);
        }
        embeddingService.persistToDatabase(embeddingDAO);

        // 2. Clear cache and reload from DB
        EmbeddingService freshEmbeddingService = new EmbeddingService(new DeterministicEmbeddingModel(64));
        assertEquals(0, freshEmbeddingService.getAllCachedEmbeddings().size());
        freshEmbeddingService.loadFromDatabase(embeddingDAO);
        assertEquals(3, freshEmbeddingService.getAllCachedEmbeddings().size());

        // 3. Query top-K
        double[] queryVec = freshEmbeddingService.embedText("knowledge graph neural networks");
        List<ScoredChunk> topResults = searchService.topKChunks(queryVec, chunks, freshEmbeddingService, 2);

        assertEquals(2, topResults.size());
        assertTrue(topResults.get(0).getChunk().getId() == c1Id || topResults.get(0).getChunk().getId() == c2Id);

        // 4. Build similarity graph edges
        List<GraphEdge> edges = searchService.buildSimilarityEdges(chunks, freshEmbeddingService, 0.60);
        assertFalse(edges.isEmpty());
        assertEquals("SIMILAR_TO", edges.get(0).getRelationType());
    }
}
```

- [ ] **Step 2: Run all tests in the project**

Run: `mvn clean test`
Expected: BUILD SUCCESS with all test suites passing

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/cortexgraph/rag/RAGIntegrationTest.java
git commit -m "test: add end-to-end integration test verifying full RAG embedding, caching, and graph pipeline"
```

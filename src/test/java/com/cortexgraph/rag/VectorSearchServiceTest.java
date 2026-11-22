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
    void testTopKEdgeCases() {
        TextChunk c1 = new TextChunk(1, 1, 0, "Sample content");
        List<TextChunk> candidates = List.of(c1);
        double[] queryVec = embeddingService.embedText("Sample");

        // k <= 0
        assertTrue(searchService.topKChunks(queryVec, candidates, embeddingService, 0).isEmpty());
        assertTrue(searchService.topKChunks(queryVec, candidates, embeddingService, -1).isEmpty());

        // empty candidates
        assertTrue(searchService.topKChunks(queryVec, List.of(), embeddingService, 5).isEmpty());
        assertTrue(searchService.topKChunks(queryVec, null, embeddingService, 5).isEmpty());

        // k > candidates size
        List<ScoredChunk> result = searchService.topKChunks(queryVec, candidates, embeddingService, 10);
        assertEquals(1, result.size());
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

    @Test
    void testBuildSimilarityEdgesEmptyAndNull() {
        assertTrue(searchService.buildSimilarityEdges(null, embeddingService, 0.5).isEmpty());
        assertTrue(searchService.buildSimilarityEdges(List.of(), embeddingService, 0.5).isEmpty());
        TextChunk c1 = new TextChunk(1, 1, 0, "Solo chunk");
        assertTrue(searchService.buildSimilarityEdges(List.of(c1), embeddingService, 0.5).isEmpty());
    }

    @Test
    void testNoSelfEdgesWhenDuplicateChunkIds() {
        TextChunk c1 = new TextChunk(1, 1, 0, "Artificial intelligence and deep neural networks");
        TextChunk c1Dup = new TextChunk(1, 1, 0, "Artificial intelligence and deep neural networks");

        List<GraphEdge> edges = searchService.buildSimilarityEdges(List.of(c1, c1Dup), embeddingService, 0.5);
        assertTrue(edges.isEmpty(), "Edges between identical chunk IDs must be prevented");
    }
}

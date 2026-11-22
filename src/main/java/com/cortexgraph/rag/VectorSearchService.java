package com.cortexgraph.rag;

import com.cortexgraph.model.GraphEdge;
import com.cortexgraph.model.ScoredChunk;
import com.cortexgraph.model.TextChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Service for semantic vector search (top-K chunks) and similarity graph edge construction.
 */
public class VectorSearchService {

    /**
     * Finds the top-K most similar text chunks for a query vector using a Min-Heap (PriorityQueue).
     * Time complexity: O(N log K), where N is number of candidates and K is the limit.
     */
    public List<ScoredChunk> topKChunks(double[] queryVector, List<TextChunk> candidateChunks,
                                        EmbeddingService embeddingService, int k) {
        if (queryVector == null || candidateChunks == null || candidateChunks.isEmpty() || k <= 0) {
            return Collections.emptyList();
        }
        if (embeddingService == null) {
            throw new IllegalArgumentException("EmbeddingService cannot be null");
        }

        // Min-heap ordered by score ascending (lowest score at the top)
        PriorityQueue<ScoredChunk> minHeap = new PriorityQueue<>(
                Comparator.comparingDouble(ScoredChunk::getScore)
        );

        for (TextChunk chunk : candidateChunks) {
            if (chunk == null) continue;
            double[] chunkVec = embeddingService.embedChunk(chunk);
            double similarity = VectorUtils.cosineSimilarity(queryVector, chunkVec);
            ScoredChunk scoredChunk = new ScoredChunk(chunk, similarity);

            if (minHeap.size() < k) {
                minHeap.offer(scoredChunk);
            } else if (minHeap.peek() != null && similarity > minHeap.peek().getScore()) {
                minHeap.poll();
                minHeap.offer(scoredChunk);
            }
        }

        List<ScoredChunk> results = new ArrayList<>(minHeap);
        results.sort(Comparator.comparingDouble(ScoredChunk::getScore).reversed());
        return results;
    }

    /**
     * Builds similarity graph edges between pairs of chunks whose cosine similarity meets or exceeds threshold.
     */
    public List<GraphEdge> buildSimilarityEdges(List<TextChunk> chunks,
                                               EmbeddingService embeddingService,
                                               double threshold) {
        if (chunks == null || chunks.size() < 2) {
            return Collections.emptyList();
        }
        if (embeddingService == null) {
            throw new IllegalArgumentException("EmbeddingService cannot be null");
        }

        List<GraphEdge> edges = new ArrayList<>();
        int n = chunks.size();

        for (int i = 0; i < n; i++) {
            TextChunk chunkI = chunks.get(i);
            if (chunkI == null) continue;
            double[] vecI = embeddingService.embedChunk(chunkI);

            for (int j = i + 1; j < n; j++) {
                TextChunk chunkJ = chunks.get(j);
                if (chunkJ == null) continue;
                double[] vecJ = embeddingService.embedChunk(chunkJ);

                double sim = VectorUtils.cosineSimilarity(vecI, vecJ);
                if (sim >= threshold) {
                    edges.add(new GraphEdge(chunkI.getId(), chunkJ.getId(), sim));
                }
            }
        }

        return edges;
    }
}

package com.cortexgraph.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic local fallback embedding model.
 * Generates an L2-normalized vector using token hashing without remote network or Ollama calls.
 */
public class DeterministicEmbeddingModel implements EmbeddingModel {
    private final int dimension;

    public DeterministicEmbeddingModel() {
        this(64);
    }

    public DeterministicEmbeddingModel(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("Dimension must be positive: " + dimension);
        }
        this.dimension = dimension;
    }

    public int getDimension() {
        return dimension;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        if (textSegments == null) {
            throw new IllegalArgumentException("Text segments list cannot be null");
        }
        List<Embedding> embeddings = new ArrayList<>(textSegments.size());
        for (TextSegment segment : textSegments) {
            embeddings.add(embedSegment(segment));
        }
        return Response.from(embeddings);
    }

    private Embedding embedSegment(TextSegment segment) {
        String text = (segment == null || segment.text() == null) ? "" : segment.text().trim();
        float[] vector = new float[dimension];

        if (!text.isEmpty()) {
            String[] tokens = text.toLowerCase().split("\\p{javaWhitespace}+");
            for (String token : tokens) {
                if (token.isEmpty()) continue;
                int hash = token.hashCode();
                int index = Math.floorMod(hash, dimension);
                vector[index] += (hash >= 0 ? 1.0f : 1.5f);
            }
        }

        // L2 normalization
        double sumSq = 0.0;
        for (float val : vector) {
            sumSq += (double) val * val;
        }

        double norm = Math.sqrt(sumSq);
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        } else {
            float uniform = (float) (1.0 / Math.sqrt(dimension));
            for (int i = 0; i < dimension; i++) {
                vector[i] = uniform;
            }
        }

        return Embedding.from(vector);
    }
}

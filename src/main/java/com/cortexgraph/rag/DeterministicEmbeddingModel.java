package com.cortexgraph.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deterministic local fallback embedding model.
 * Generates an L2-normalized vector using token hashing with stopword filtering
 * without remote network or Ollama calls.
 */
public class DeterministicEmbeddingModel implements EmbeddingModel {

    public static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the",
            "in", "on", "at", "to", "for", "of", "with", "by", "from",
            "is", "are", "was", "were", "be", "been", "being",
            "what", "when", "where", "which", "who", "whom", "whose", "why", "how",
            "and", "or", "but", "not", "so",
            "this", "that", "these", "those",
            "it", "its", "as", "do", "does", "did", "can", "could", "would", "should",
            "tell", "me", "about", "explain"
    );

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
            String[] rawTokens = text.toLowerCase().split("\\p{javaWhitespace}+");
            List<String> contentTokens = new ArrayList<>();
            for (String raw : rawTokens) {
                String token = cleanToken(raw);
                if (!token.isEmpty() && !STOPWORDS.contains(token)) {
                    contentTokens.add(token);
                }
            }

            // If all tokens were stopwords, fall back to all cleaned tokens
            if (contentTokens.isEmpty()) {
                for (String raw : rawTokens) {
                    String token = cleanToken(raw);
                    if (!token.isEmpty()) {
                        contentTokens.add(token);
                    }
                }
            }

            for (String token : contentTokens) {
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

    private String cleanToken(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "");
    }
}

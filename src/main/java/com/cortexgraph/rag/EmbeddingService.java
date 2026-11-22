package com.cortexgraph.rag;

import com.cortexgraph.dao.EmbeddingDAO;
import com.cortexgraph.exception.VaultException;
import com.cortexgraph.model.TextChunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing embedding generation, in-memory caching, and SQLite synchronization.
 */
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;
    private final ConcurrentHashMap<String, double[]> cache = new ConcurrentHashMap<>();

    public EmbeddingService() {
        this(new DeterministicEmbeddingModel(64));
    }

    public EmbeddingService(EmbeddingModel embeddingModel) {
        if (embeddingModel == null) {
            throw new IllegalArgumentException("Embedding model cannot be null");
        }
        this.embeddingModel = embeddingModel;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Embeds a raw text string into a normalized vector.
     */
    public double[] embedText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        Response<List<Embedding>> response = embeddingModel.embedAll(List.of(TextSegment.from(text)));
        List<Embedding> embeddings = response.content();
        if (embeddings == null || embeddings.isEmpty()) {
            throw new IllegalStateException("Embedding model produced empty response");
        }

        float[] floatVec = embeddings.get(0).vector();
        double[] doubleVec = new double[floatVec.length];
        for (int i = 0; i < floatVec.length; i++) {
            doubleVec[i] = floatVec[i];
        }
        return VectorUtils.normalize(doubleVec);
    }

    /**
     * Embeds a text chunk and caches the vector using chunk ID as the key.
     */
    public double[] embedChunk(TextChunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk cannot be null");
        }
        String key = String.valueOf(chunk.getId());
        double[] cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        double[] vector = embedText(chunk.getContent());
        cache.put(key, vector);
        return vector;
    }

    /**
     * Retrieves a cached embedding vector by key.
     */
    public double[] getCachedEmbedding(String key) {
        return cache.get(key);
    }

    /**
     * Stores an embedding vector directly into the cache.
     */
    public void cacheEmbedding(String key, double[] vector) {
        if (key != null && vector != null) {
            cache.put(key, vector);
        }
    }

    /**
     * Returns an unmodifiable view of all cached embeddings.
     */
    public Map<String, double[]> getAllCachedEmbeddings() {
        return Collections.unmodifiableMap(cache);
    }

    /**
     * Loads embeddings from SQLite database into the in-memory cache.
     */
    public void loadFromDatabase(EmbeddingDAO dao) throws VaultException {
        if (dao == null) {
            throw new IllegalArgumentException("EmbeddingDAO cannot be null");
        }
        Map<Integer, double[]> stored = dao.findAll();
        for (Map.Entry<Integer, double[]> entry : stored.entrySet()) {
            cache.put(String.valueOf(entry.getKey()), entry.getValue());
        }
    }

    /**
     * Persists in-memory chunk embeddings to SQLite database.
     */
    public void persistToDatabase(EmbeddingDAO dao) throws VaultException {
        if (dao == null) {
            throw new IllegalArgumentException("EmbeddingDAO cannot be null");
        }
        Map<Integer, double[]> toSave = new HashMap<>();
        for (Map.Entry<String, double[]> entry : cache.entrySet()) {
            try {
                int chunkId = Integer.parseInt(entry.getKey());
                toSave.put(chunkId, entry.getValue());
            } catch (NumberFormatException ignored) {
                // Skip non-integer keys (e.g., query strings or custom identifiers)
            }
        }
        dao.saveAll(toSave);
    }
}

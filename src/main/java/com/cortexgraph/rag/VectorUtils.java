package com.cortexgraph.rag;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

/**
 * Mathematical and serialization utilities for vector embeddings.
 */
public class VectorUtils {

    private VectorUtils() {
        // Utility class with static methods
    }

    /**
     * Computes the cosine similarity between two double vectors.
     * Returns 0.0 safely if either vector is null, empty, of different lengths, or a zero vector.
     */
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Serializes a double array into a byte array using standard IEEE 754 64-bit precision.
     */
    public static byte[] toByteArray(double[] vector) {
        if (vector == null) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Double.BYTES);
        for (double d : vector) {
            buffer.putDouble(d);
        }
        return buffer.array();
    }

    /**
     * Deserializes a byte array back into a double array.
     */
    public static double[] fromByteArray(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new double[0];
        }
        DoubleBuffer doubleBuffer = ByteBuffer.wrap(bytes).asDoubleBuffer();
        double[] vector = new double[doubleBuffer.remaining()];
        doubleBuffer.get(vector);
        return vector;
    }

    /**
     * Returns an L2-normalized unit vector (Euclidean norm = 1.0).
     */
    public static double[] normalize(double[] vector) {
        if (vector == null) {
            return new double[0];
        }
        double sumSq = 0.0;
        for (double d : vector) {
            sumSq += d * d;
        }
        double norm = Math.sqrt(sumSq);
        double[] result = new double[vector.length];
        if (norm == 0.0) {
            return result;
        }
        for (int i = 0; i < vector.length; i++) {
            result[i] = vector[i] / norm;
        }
        return result;
    }
}

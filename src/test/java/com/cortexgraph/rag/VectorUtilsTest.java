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
        assertEquals(0.0, VectorUtils.cosineSimilarity(new double[0], new double[0]));
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

    @Test
    void testNormalize() {
        double[] v = {3.0, 4.0};
        double[] normalized = VectorUtils.normalize(v);
        assertEquals(0.6, normalized[0], 1e-6);
        assertEquals(0.8, normalized[1], 1e-6);

        double norm = Math.sqrt(normalized[0] * normalized[0] + normalized[1] * normalized[1]);
        assertEquals(1.0, norm, 1e-6);

        // Zero vector normalize
        double[] zero = {0.0, 0.0};
        double[] normZero = VectorUtils.normalize(zero);
        assertEquals(0.0, normZero[0]);
        assertEquals(0.0, normZero[1]);
    }
}

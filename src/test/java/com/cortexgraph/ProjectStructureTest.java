package com.cortexgraph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProjectStructureTest {
    @Test
    void testPackagesExist() {
        assertNotNull(getClass().getClassLoader().getResource("com/cortexgraph/parser/package-info.class"));
        assertNotNull(getClass().getClassLoader().getResource("com/cortexgraph/rag/package-info.class"));
        assertNotNull(getClass().getClassLoader().getResource("com/cortexgraph/ui/package-info.class"));
    }
}

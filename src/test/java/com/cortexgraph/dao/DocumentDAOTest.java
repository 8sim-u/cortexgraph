package com.cortexgraph.dao;

import com.cortexgraph.model.DocumentRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentDAOTest {

    private DocumentDAO dao;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("doc_test.db");
        DatabaseManager.initializeDatabase(dbUrl);
        dao = new DocumentDAO(dbUrl);
    }

    @Test
    void testSaveFindAndListDocuments() throws Exception {
        DocumentRecord doc = new DocumentRecord("guide.pdf", "/vault/guide.pdf", 4096L);
        int generatedId = dao.save(doc);
        assertTrue(generatedId > 0);

        Optional<DocumentRecord> fetched = dao.findById(generatedId);
        assertTrue(fetched.isPresent());
        assertEquals("guide.pdf", fetched.get().getFilename());
        assertEquals("/vault/guide.pdf", fetched.get().getFilePath());
        assertEquals(4096L, fetched.get().getFileSize());
        assertNotNull(fetched.get().getCreatedAt());

        List<DocumentRecord> list = dao.findAll();
        assertEquals(1, list.size());
        assertEquals(generatedId, list.get(0).getId());

        boolean deleted = dao.delete(generatedId);
        assertTrue(deleted);
        assertTrue(dao.findById(generatedId).isEmpty());
    }

    @Test
    void testFindNonExistentReturnsEmpty() throws Exception {
        Optional<DocumentRecord> missing = dao.findById(999);
        assertTrue(missing.isEmpty());
    }
}

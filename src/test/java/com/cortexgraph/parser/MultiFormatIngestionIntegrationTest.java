package com.cortexgraph.parser;

import com.cortexgraph.dao.ChunkDAO;
import com.cortexgraph.dao.DatabaseManager;
import com.cortexgraph.dao.DocumentDAO;
import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.TextChunk;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class MultiFormatIngestionIntegrationTest {

    @Test
    void testConcurrentMultiFormatIngestionAndPersistence(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("ingest_integration.db");
        DatabaseManager.initializeDatabase(dbUrl);
        DocumentDAO docDAO = new DocumentDAO(dbUrl);
        ChunkDAO chunkDAO = new ChunkDAO(dbUrl);

        // 1. Create a PDF with 2 pages
        Path pdfPath = tempDir.resolve("doc1.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage p1 = new PDPage();
            doc.addPage(p1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p1)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText("PDF Page 1 knowledge base introduction");
                cs.endText();
            }

            PDPage p2 = new PDPage();
            doc.addPage(p2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p2)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText("PDF Page 2 detailed architecture overview");
                cs.endText();
            }
            doc.save(pdfPath.toFile());
        }

        // 2. Create a Markdown file
        Path mdPath = tempDir.resolve("doc2.md");
        Files.writeString(mdPath, "# Architecture Overview\n\nThis is the markdown overview section with content.");

        // 3. Register documents in DB
        int pdfDocId = docDAO.save(new DocumentRecord("doc1.pdf", pdfPath.toString(), Files.size(pdfPath)));
        int mdDocId = docDAO.save(new DocumentRecord("doc2.md", mdPath.toString(), Files.size(mdPath)));
        assertTrue(pdfDocId > 0);
        assertTrue(mdDocId > 0);

        // 4. Ingest asynchronously using thread pool
        try (DocumentIngestor ingestor = new DocumentIngestor(4)) {
            CompletableFuture<List<TextChunk>> f1 = ingestor.ingestAsync(pdfDocId, pdfPath.toFile());
            CompletableFuture<List<TextChunk>> f2 = ingestor.ingestAsync(mdDocId, mdPath.toFile());

            CompletableFuture.allOf(f1, f2).join();

            List<TextChunk> pdfChunks = f1.get();
            List<TextChunk> mdChunks = f2.get();

            assertNotNull(pdfChunks);
            assertNotNull(mdChunks);
            assertEquals(2, pdfChunks.size()); // 1 chunk per page
            assertEquals(1, mdChunks.size());

            // Save all chunks to SQLite
            chunkDAO.saveAll(pdfChunks);
            chunkDAO.saveAll(mdChunks);
        }

        // 5. Verify database records
        List<TextChunk> savedPdfChunks = chunkDAO.findByDocumentId(pdfDocId);
        assertEquals(2, savedPdfChunks.size());
        assertEquals(0, savedPdfChunks.get(0).getChunkIndex());
        assertEquals(1, savedPdfChunks.get(1).getChunkIndex());
        assertTrue(savedPdfChunks.get(0).getContent().contains("PDF Page 1"));
        assertTrue(savedPdfChunks.get(1).getContent().contains("PDF Page 2"));

        List<TextChunk> savedMdChunks = chunkDAO.findByDocumentId(mdDocId);
        assertEquals(1, savedMdChunks.size());
        assertTrue(savedMdChunks.get(0).getContent().contains("Architecture Overview"));
    }
}

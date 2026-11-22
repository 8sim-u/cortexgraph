package com.cortexgraph.parser;

import com.cortexgraph.exception.DocumentParseException;
import com.cortexgraph.model.ParsedDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PdfDocumentParserTest {
    private PdfDocumentParser parser;

    @BeforeEach
    void setUp() {
        parser = new PdfDocumentParser();
    }

    private File createTestPdf(Path dest, String page1Text, String page2Text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage p1 = new PDPage();
            doc.addPage(p1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p1)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText(page1Text);
                cs.endText();
            }

            if (page2Text != null) {
                PDPage p2 = new PDPage();
                doc.addPage(p2);
                try (PDPageContentStream cs = new PDPageContentStream(doc, p2)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.showText(page2Text);
                    cs.endText();
                }
            }
            File file = dest.toFile();
            doc.save(file);
            return file;
        }
    }

    @Test
    void testParseMultiPagePdfFile(@TempDir Path tempDir) throws Exception {
        File pdfFile = createTestPdf(tempDir.resolve("multi.pdf"), "First page heading", "Second page body");
        ParsedDocument doc = parser.parse(pdfFile);

        assertEquals("multi.pdf", doc.getFilename());
        assertEquals(2, doc.getPages().size());
        assertEquals(1, doc.getPages().get(0).getPageNumber());
        assertTrue(doc.getPages().get(0).getContent().contains("First page heading"));

        assertEquals(2, doc.getPages().get(1).getPageNumber());
        assertTrue(doc.getPages().get(1).getContent().contains("Second page body"));
    }

    @Test
    void testParsePdfInputStream(@TempDir Path tempDir) throws Exception {
        File pdfFile = createTestPdf(tempDir.resolve("stream.pdf"), "Stream PDF text", null);
        try (FileInputStream fis = new FileInputStream(pdfFile)) {
            ParsedDocument doc = parser.parse(fis, "stream.pdf");
            assertEquals("stream.pdf", doc.getFilename());
            assertEquals(1, doc.getPages().size());
            assertTrue(doc.getPages().get(0).getContent().contains("Stream PDF text"));
        }
    }

    @Test
    void testParseCorruptPdfThrowsException() {
        byte[] corruptBytes = "Not a valid PDF format at all".getBytes();
        ByteArrayInputStream stream = new ByteArrayInputStream(corruptBytes);
        assertThrows(DocumentParseException.class, () -> parser.parse(stream, "corrupt.pdf"));
    }

    @Test
    void testParseNonExistentFileThrowsException() {
        assertThrows(DocumentParseException.class, () -> parser.parse(new File("/non/existent.pdf")));
    }
}

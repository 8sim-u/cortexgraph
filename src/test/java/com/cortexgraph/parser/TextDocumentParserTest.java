package com.cortexgraph.parser;

import com.cortexgraph.exception.DocumentParseException;
import com.cortexgraph.model.ParsedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class TextDocumentParserTest {
    private TextDocumentParser parser;

    @BeforeEach
    void setUp() {
        parser = new TextDocumentParser();
    }

    @Test
    void testParseTextFile(@TempDir Path tempDir) throws Exception {
        Path txtPath = tempDir.resolve("sample.txt");
        Files.writeString(txtPath, "Line 1\nLine 2 with words\nLine 3");
        ParsedDocument doc = parser.parse(txtPath.toFile());

        assertEquals("sample.txt", doc.getFilename());
        assertEquals(1, doc.getPages().size());
        assertEquals(1, doc.getPages().get(0).getPageNumber());
        assertTrue(doc.getFullText().contains("Line 2 with words"));
    }

    @Test
    void testParseMarkdownStream() throws Exception {
        String mdContent = "# CortexGraph\n\n- Feature A\n- Feature B";
        ByteArrayInputStream stream = new ByteArrayInputStream(mdContent.getBytes(StandardCharsets.UTF_8));
        ParsedDocument doc = parser.parse(stream, "notes.md");

        assertEquals("notes.md", doc.getFilename());
        assertEquals(1, doc.getPages().size());
        assertTrue(doc.getFullText().contains("# CortexGraph"));
    }

    @Test
    void testParseEmptyFileThrowsException(@TempDir Path tempDir) throws Exception {
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, "   \n\t  ");
        assertThrows(DocumentParseException.class, () -> parser.parse(emptyFile.toFile()));
    }

    @Test
    void testParseNonExistentFileThrowsException() {
        File nonExistent = new File("/non/existent/path/doc.txt");
        assertThrows(DocumentParseException.class, () -> parser.parse(nonExistent));
    }
}

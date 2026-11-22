package com.cortexgraph.parser;

import com.cortexgraph.exception.DocumentParseException;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentIngestorTest {
    private DocumentIngestor ingestor;

    @BeforeEach
    void setUp() {
        ingestor = new DocumentIngestor(2);
    }

    @AfterEach
    void tearDown() {
        ingestor.close();
    }

    @Test
    void testAsyncIngestionOfTextFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("async_test.txt");
        Files.writeString(file, "Word1 Word2 Word3 Word4 Word5 Word6 Word7 Word8 Word9 Word10");

        CompletableFuture<List<TextChunk>> future = ingestor.ingestAsync(101, file.toFile());
        List<TextChunk> chunks = future.get();

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        assertEquals(101, chunks.get(0).getDocumentId());
        assertTrue(chunks.get(0).getContent().contains("Word1"));
    }

    @Test
    void testAsyncIngestionOfStream() throws Exception {
        String content = "Markdown stream content line one line two";
        ByteArrayInputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        CompletableFuture<List<TextChunk>> future = ingestor.ingestAsync(102, stream, "test.md");
        List<TextChunk> chunks = future.get();

        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals(102, chunks.get(0).getDocumentId());
        assertEquals(1, chunks.get(0).getPageNumber());
    }

    @Test
    void testUnsupportedExtensionFailsFuture(@TempDir Path tempDir) throws Exception {
        Path unsupported = tempDir.resolve("archive.zip");
        Files.writeString(unsupported, "dummy binary data");

        CompletableFuture<List<TextChunk>> future = ingestor.ingestAsync(103, unsupported.toFile());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(DocumentParseException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Unsupported file extension"));
    }

    @Test
    void testCleanShutdown() {
        assertDoesNotThrow(() -> ingestor.shutdown());
        assertTrue(ingestor.isShutdown());
    }
}

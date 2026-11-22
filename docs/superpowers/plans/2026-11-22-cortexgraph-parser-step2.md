# CortexGraph Step 2: Document Parsing & Text Chunking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Step 2 in `com.cortexgraph.parser` (and supporting models in `com.cortexgraph.model`) to parse PDF, TXT, and Markdown files, split extracted text into overlapping chunks (~250 words with ~40 words overlap) preserving page metadata, and asynchronously ingest documents via an `ExecutorService` thread pool.

**Architecture:** A parser hierarchy (`DocumentParser` interface implemented by `TextDocumentParser` and `PdfDocumentParser`) returns a `ParsedDocument` containing sequential `ParsedPage` objects. A dedicated `TextChunker` converts parsed pages into overlapping `TextChunk` domain models while tracking `chunkIndex` and `pageNumber`. A `DocumentIngestor` coordinates parsing and chunking on a background `ExecutorService` thread pool with an `AutoCloseable` lifecycle.

**Tech Stack:** Java 21 (I/O streams, Concurrency), Apache PDFBox 3.0.3 (`Loader.loadPDF`, `PDFTextStripper`), JUnit Jupiter 5.10.2.

**Spec:** Direct user request for Step 2: `DocumentParser` interface, `PdfDocumentParser` (PDFBox `Loader.loadPDF` + `BufferedInputStream`), `TextDocumentParser` (`BufferedReader` + `InputStreamReader` for `.txt` and `.md`), `TextChunker` (overlapping ~250 words, ~40 overlap, metadata, `DocumentParseException` on empty/invalid), `DocumentIngestor` (`ExecutorService` thread pool, async execution, clean shutdown), and unit/integration tests.

---

## Global Constraints

- Java version: 21 (using `BufferedReader`, `BufferedInputStream`, `Loader.loadPDF`, `ExecutorService`, `CompletableFuture`).
- Packaging: parsers in `com.cortexgraph.parser`, models in `com.cortexgraph.model`, exceptions in `com.cortexgraph.exception`.
- Error handling: any I/O failure, corrupt file, unsupported format, or empty content throws checked `DocumentParseException`.
- Resource management: every stream, reader, `PDDocument`, and `ExecutorService` must be managed with try-with-resources or explicit clean shutdown.
- Compatibility: existing Step 1 models (`TextChunk`, `DocumentRecord`, `ChatMessage`) and database tests must remain backward compatible.

---

## Review Focus

1. **Empty / Blank File Handling**: `TextChunker` and parsers must detect empty or whitespace-only files and throw `DocumentParseException` rather than creating 0-length chunks.
2. **Chunk Boundary Arithmetic**: A document with fewer words than `chunkSize` (e.g. 50 words) must produce exactly 1 chunk; documents spanning multiple windows must correctly apply overlap without duplicate chunks or out-of-bounds indexing.
3. **Multi-Page Metadata Alignment**: For multi-page PDFs, chunks extracted from each page must preserve that page's specific `pageNumber` while `chunkIndex` increments continuously (0, 1, 2, ...).
4. **Corrupt / Non-PDF Input Rejection**: Passing an invalid or non-PDF file to `PdfDocumentParser` must throw `DocumentParseException` wrapping the underlying `IOException`.
5. **Ingestor Thread Pool Shutdown**: `DocumentIngestor.shutdown()` and `awaitTermination` must guarantee thread termination without hanging background threads.

---

### Task 1: Parser Domain Models (`ParsedPage`, `ParsedDocument`, & `TextChunk` Page Metadata)

**Files:**
- Create: `src/main/java/com/cortexgraph/model/ParsedPage.java`
- Create: `src/main/java/com/cortexgraph/model/ParsedDocument.java`
- Modify: `src/main/java/com/cortexgraph/model/TextChunk.java`
- Test: `src/test/java/com/cortexgraph/model/ParsedDocumentTest.java`

**Interfaces:**
- Produces:
  - `ParsedPage(int pageNumber, String content)`
  - `ParsedDocument(String filename, List<ParsedPage> pages)` + `String getFullText()`
  - `TextChunk` enhanced with `int pageNumber` (defaulting to 1 for backward compatibility)

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ParsedDocumentTest {
    @Test
    void testParsedDocumentAndPages() {
        ParsedPage p1 = new ParsedPage(1, "Page 1 intro");
        ParsedPage p2 = new ParsedPage(2, "Page 2 conclusion");
        ParsedDocument doc = new ParsedDocument("sample.pdf", List.of(p1, p2));

        assertEquals("sample.pdf", doc.getFilename());
        assertEquals(2, doc.getPages().size());
        assertEquals("Page 1 intro\n\nPage 2 conclusion", doc.getFullText());
    }

    @Test
    void testTextChunkPageNumberBackwardCompatibility() {
        // Old 3-arg constructor defaults pageNumber to 1
        TextChunk oldChunk = new TextChunk(10, 0, "Hello");
        assertEquals(1, oldChunk.getPageNumber());

        // New constructor preserves explicit pageNumber
        TextChunk newChunk = new TextChunk(10, 0, "Hello", 3);
        assertEquals(3, newChunk.getPageNumber());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ParsedDocumentTest`
Expected: FAIL (cannot find symbol `ParsedPage`, `ParsedDocument`, `getPageNumber`)

- [ ] **Step 3: Implement `ParsedPage`, `ParsedDocument`, and update `TextChunk`**

- Create `ParsedPage(int pageNumber, String content)` with getters and validation.
- Create `ParsedDocument(String filename, List<ParsedPage> pages)` with `getFullText()`.
- Add `int pageNumber` field and overloaded constructors to `TextChunk` defaulting `pageNumber` to 1.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ParsedDocumentTest`
Expected: Tests run: 2, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/model/ src/test/java/com/cortexgraph/model/
git commit -m "feat: add ParsedPage and ParsedDocument models, update TextChunk with page metadata"
```

---

### Task 2: `DocumentParser` Interface & `TextDocumentParser`

**Files:**
- Create: `src/main/java/com/cortexgraph/parser/DocumentParser.java`
- Create: `src/main/java/com/cortexgraph/parser/TextDocumentParser.java`
- Test: `src/test/java/com/cortexgraph/parser/TextDocumentParserTest.java`

**Interfaces:**
- Consumes: `DocumentParseException` from `com.cortexgraph.exception`, `ParsedDocument`, `ParsedPage` from `com.cortexgraph.model`
- Produces:
  - `DocumentParser` interface: `ParsedDocument parse(File file) throws DocumentParseException;` and `ParsedDocument parse(InputStream inputStream, String filename) throws DocumentParseException;`
  - `TextDocumentParser` implementing `DocumentParser` for `.txt` and `.md` files using `BufferedReader` and `InputStreamReader`.

- [ ] **Step 1: Write the failing test**

```java
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TextDocumentParserTest`
Expected: FAIL (cannot find symbol `DocumentParser`, `TextDocumentParser`)

- [ ] **Step 3: Implement `DocumentParser` and `TextDocumentParser`**

- Define `DocumentParser` interface with `parse(File)` and `parse(InputStream, String)`.
- Implement `TextDocumentParser` using `BufferedReader` and `InputStreamReader(..., StandardCharsets.UTF_8)`.
- Check for empty/blank content and throw `DocumentParseException("File is empty: ...")`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=TextDocumentParserTest`
Expected: Tests run: 3, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/parser/ src/test/java/com/cortexgraph/parser/
git commit -m "feat: implement DocumentParser interface and TextDocumentParser for txt and md"
```

---

### Task 3: `PdfDocumentParser` (Apache PDFBox)

**Files:**
- Create: `src/main/java/com/cortexgraph/parser/PdfDocumentParser.java`
- Test: `src/test/java/com/cortexgraph/parser/PdfDocumentParserTest.java`

**Interfaces:**
- Consumes: `DocumentParser`, `DocumentParseException`, `ParsedDocument`, `ParsedPage`
- Produces: `PdfDocumentParser implements DocumentParser` using `Loader.loadPDF` and `BufferedInputStream` to extract text with page info.

- [ ] **Step 1: Write the failing test**

```java
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
import java.nio.file.Files;
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
    void testParseMultiPagePdf(@TempDir Path tempDir) throws Exception {
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
    void testParseCorruptPdfThrowsException() {
        byte[] corruptBytes = "Not a real PDF".getBytes();
        ByteArrayInputStream stream = new ByteArrayInputStream(corruptBytes);
        assertThrows(DocumentParseException.class, () -> parser.parse(stream, "corrupt.pdf"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=PdfDocumentParserTest`
Expected: FAIL (cannot find symbol `PdfDocumentParser`)

- [ ] **Step 3: Implement `PdfDocumentParser`**

- Use `BufferedInputStream` to buffer input streams.
- Use `Loader.loadPDF(file)` or `Loader.loadPDF(bytes)` inside try-with-resources.
- Iterate over pages (`1..document.getNumberOfPages()`) using `PDFTextStripper` with `setStartPage` and `setEndPage`.
- Validate that extracted text is not empty; throw `DocumentParseException` if empty or on `IOException`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=PdfDocumentParserTest`
Expected: Tests run: 2, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/parser/PdfDocumentParser.java src/test/java/com/cortexgraph/parser/PdfDocumentParserTest.java
git commit -m "feat: implement PdfDocumentParser using Apache PDFBox Loader"
```

---

### Task 4: `TextChunker` (Overlapping Word-Based Chunks & Metadata)

**Files:**
- Create: `src/main/java/com/cortexgraph/parser/TextChunker.java`
- Test: `src/test/java/com/cortexgraph/parser/TextChunkerTest.java`

**Interfaces:**
- Consumes: `ParsedDocument`, `ParsedPage`, `TextChunk`, `DocumentParseException`
- Produces: `TextChunker` with `chunk(int documentId, ParsedDocument doc)` and configurable `(chunkSize, overlap)` (defaulting to 250 words and 40 words overlap).

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph.parser;

import com.cortexgraph.exception.DocumentParseException;
import com.cortexgraph.model.ParsedDocument;
import com.cortexgraph.model.ParsedPage;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TextChunkerTest {
    private TextChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new TextChunker(10, 2); // 10 words per chunk, 2 words overlap for testability
    }

    @Test
    void testShortDocumentCreatesSingleChunk() throws Exception {
        ParsedDocument doc = new ParsedDocument("short.txt", List.of(
            new ParsedPage(1, "One two three four five")
        ));
        List<TextChunk> chunks = chunker.chunk(1, doc);

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals("One two three four five", chunks.get(0).getContent());
        assertEquals(1, chunks.get(0).getPageNumber());
    }

    @Test
    void testOverlappingChunkBoundaries() throws Exception {
        // 18 words total
        String text = "w1 w2 w3 w4 w5 w6 w7 w8 w9 w10 w11 w12 w13 w14 w15 w16 w17 w18";
        ParsedDocument doc = new ParsedDocument("doc.txt", List.of(new ParsedPage(1, text)));
        List<TextChunk> chunks = chunker.chunk(1, doc);

        // Chunk 0: w1..w10 (10 words)
        // Step: 10 - 2 = 8 words -> Chunk 1: w9..w18 (10 words)
        assertEquals(2, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertTrue(chunks.get(0).getContent().startsWith("w1"));
        assertTrue(chunks.get(0).getContent().endsWith("w10"));

        assertEquals(1, chunks.get(1).getChunkIndex());
        assertTrue(chunks.get(1).getContent().startsWith("w9"));
        assertTrue(chunks.get(1).getContent().endsWith("w18"));
    }

    @Test
    void testMultiPageMetadataPreserved() throws Exception {
        ParsedDocument doc = new ParsedDocument("multi.txt", List.of(
            new ParsedPage(1, "Page one text"),
            new ParsedPage(2, "Page two text")
        ));
        List<TextChunk> chunks = chunker.chunk(1, doc);

        assertEquals(2, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals(1, chunks.get(0).getPageNumber());
        assertEquals(1, chunks.get(1).getChunkIndex());
        assertEquals(2, chunks.get(1).getPageNumber());
    }

    @Test
    void testEmptyContentThrowsException() {
        ParsedDocument emptyDoc = new ParsedDocument("empty.txt", List.of(new ParsedPage(1, "   ")));
        assertThrows(DocumentParseException.class, () -> chunker.chunk(1, emptyDoc));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TextChunkerTest`
Expected: FAIL (cannot find symbol `TextChunker`)

- [ ] **Step 3: Implement `TextChunker`**

- Defaults: `DEFAULT_CHUNK_SIZE = 250`, `DEFAULT_OVERLAP = 40`.
- Constructor `TextChunker()` and `TextChunker(int chunkSize, int overlap)`.
- Validates `chunkSize > 0` and `0 <= overlap < chunkSize`.
- Splits pages into words (`\\s+`). Rejects empty content with `DocumentParseException`.
- Slides window: `step = chunkSize - overlap`.
- Generates `TextChunk(0, documentId, chunkIndex++, chunkContent, pageNumber)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=TextChunkerTest`
Expected: Tests run: 4, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/parser/TextChunker.java src/test/java/com/cortexgraph/parser/TextChunkerTest.java
git commit -m "feat: implement TextChunker with overlapping sliding window and page metadata"
```

---

### Task 5: `DocumentIngestor` (Asynchronous Thread Pool Ingestion)

**Files:**
- Create: `src/main/java/com/cortexgraph/parser/DocumentIngestor.java`
- Test: `src/test/java/com/cortexgraph/parser/DocumentIngestorTest.java`

**Interfaces:**
- Consumes: `DocumentParser`, `TextDocumentParser`, `PdfDocumentParser`, `TextChunker`, `TextChunk`, `DocumentParseException`
- Produces:
  - `DocumentIngestor implements AutoCloseable`
  - `CompletableFuture<List<TextChunk>> ingestAsync(int documentId, File file)`
  - `CompletableFuture<List<TextChunk>> ingestAsync(int documentId, InputStream stream, String filename)`
  - `void shutdown()`, `boolean awaitTermination(long timeout, TimeUnit unit)`

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph.parser;

import com.cortexgraph.exception.DocumentParseException;
import com.cortexgraph.model.TextChunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
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
        ingestor = new DocumentIngestor(2); // 2 worker threads
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
    }

    @Test
    void testUnsupportedExtensionFailsFuture(@TempDir Path tempDir) throws Exception {
        Path unsupported = tempDir.resolve("archive.zip");
        Files.writeString(unsupported, "dummy binary data");

        CompletableFuture<List<TextChunk>> future = ingestor.ingestAsync(102, unsupported.toFile());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause() instanceof DocumentParseException);
        assertTrue(ex.getCause().getMessage().contains("Unsupported file extension"));
    }

    @Test
    void testCleanShutdown() {
        assertDoesNotThrow(() -> ingestor.shutdown());
        assertTrue(ingestor.isShutdown());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DocumentIngestorTest`
Expected: FAIL (cannot find symbol `DocumentIngestor`)

- [ ] **Step 3: Implement `DocumentIngestor`**

- Fields: `ExecutorService executor`, `TextChunker chunker`, `TextDocumentParser textParser`, `PdfDocumentParser pdfParser`.
- Method `getParserFor(String filename)`: inspects extension (`.pdf` -> pdfParser, `.txt`/`.md` -> textParser, else throws `DocumentParseException`).
- `ingestAsync`: returns `CompletableFuture.supplyAsync(() -> { ... }, executor)`.
- Implements `AutoCloseable`: `close()` calls `shutdown()` and `awaitTermination`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DocumentIngestorTest`
Expected: Tests run: 3, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/parser/DocumentIngestor.java src/test/java/com/cortexgraph/parser/DocumentIngestorTest.java
git commit -m "feat: implement DocumentIngestor with async ExecutorService thread pool"
```

---

### Task 6: Multi-Format End-to-End Ingestion Integration Test & Full Suite Run

**Files:**
- Create: `src/test/java/com/cortexgraph/parser/MultiFormatIngestionIntegrationTest.java`

- [ ] **Step 1: Write integration test exercising parallel parsing of PDF, TXT, and Markdown files and persistence to SQLite**

```java
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

        // 1. Create PDF
        Path pdfPath = tempDir.resolve("doc1.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText("PDF knowledge base document paragraph");
                cs.endText();
            }
            doc.save(pdfPath.toFile());
        }

        // 2. Create Markdown
        Path mdPath = tempDir.resolve("doc2.md");
        Files.writeString(mdPath, "# Architecture\n\nThis is the markdown overview section.");

        // 3. Register documents in DB
        int pdfDocId = docDAO.save(new DocumentRecord("doc1.pdf", pdfPath.toString(), Files.size(pdfPath)));
        int mdDocId = docDAO.save(new DocumentRecord("doc2.md", mdPath.toString(), Files.size(mdPath)));

        // 4. Ingest asynchronously
        try (DocumentIngestor ingestor = new DocumentIngestor(4)) {
            CompletableFuture<List<TextChunk>> f1 = ingestor.ingestAsync(pdfDocId, pdfPath.toFile());
            CompletableFuture<List<TextChunk>> f2 = ingestor.ingestAsync(mdDocId, mdPath.toFile());

            CompletableFuture.allOf(f1, f2).join();

            List<TextChunk> pdfChunks = f1.get();
            List<TextChunk> mdChunks = f2.get();

            chunkDAO.saveAll(pdfChunks);
            chunkDAO.saveAll(mdChunks);
        }

        // 5. Verify database records
        assertEquals(1, chunkDAO.findByDocumentId(pdfDocId).size());
        assertEquals(1, chunkDAO.findByDocumentId(mdDocId).size());
        assertTrue(chunkDAO.findByDocumentId(pdfDocId).get(0).getContent().contains("PDF knowledge base"));
        assertTrue(chunkDAO.findByDocumentId(mdDocId).get(0).getContent().contains("Architecture"));
    }
}
```

- [ ] **Step 2: Run all tests in the project**

Run: `mvn clean test`
Expected: BUILD SUCCESS with all test suites passing (both Step 1 and Step 2 tests)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/cortexgraph/parser/MultiFormatIngestionIntegrationTest.java
git commit -m "test: add integration test verifying concurrent ingestion of PDF and Markdown with database persistence"
```

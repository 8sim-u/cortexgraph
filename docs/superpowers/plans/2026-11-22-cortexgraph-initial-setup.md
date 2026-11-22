# CortexGraph Initial Project Setup & Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Initialize a clean, beginner-friendly Maven Java 21 + JavaFX project named `CortexGraph` with all specified package structures, custom exceptions, domain models, SQLite database management, and DAO classes.

**Architecture:** A lightweight, layered architecture where domain models represent data structures without framework coupling; custom checked exceptions provide explicit failure signals; a centralized `DatabaseManager` handles SQLite connections and schema initialization; and simple DAO classes execute CRUD operations using JDBC `PreparedStatement` and try-with-resources.

**Tech Stack:** Java 21, Maven 3.9+, JavaFX Controls (21.0.2), Apache PDFBox (3.0.3), SQLite JDBC (3.45.2.0), LangChain4j (0.35.0), and JUnit Jupiter (5.10.2).

**Spec:** Direct user request specifying Maven Java 21 setup, package tree, checked exceptions (`DocumentParseException`, `VaultException`), models (`DocumentRecord`, `TextChunk`, `ChatMessage`), `DatabaseManager` with SQLite schema for `documents`, `chunks`, `chat_history`, and simple DAOs (`DocumentDAO`, `ChunkDAO`, `ChatDAO`).

---

## Global Constraints

- Java version: 21 (compiled with `<maven.compiler.release>21</maven.compiler.release>`).
- Database: SQLite with default file `jdbc:sqlite:cortexgraph.db` and parameterized constructors to allow in-memory or custom test DB URLs (`jdbc:sqlite::memory:`).
- JDBC access: standard `DriverManager`, `PreparedStatement`, and try-with-resources only (no ORM or heavy frameworks).
- Exceptions: custom checked exceptions extending `java.lang.Exception` for business/data failures; DAOs must wrap `SQLException` into `VaultException`.
- Code style: clean, readable, well-commented, beginner-friendly, and free of over-engineering.

---

## Review Focus

1. **Foreign Key Enforcement**: SQLite does not enable foreign keys by default unless `PRAGMA foreign_keys = ON;` is executed on the connection. Inserting chunks with invalid `document_id` should be caught.
2. **Schema Idempotency**: Running `DatabaseManager.initializeDatabase()` multiple times on an existing database file must succeed safely without errors or data corruption (`CREATE TABLE IF NOT EXISTS`).
3. **Exception Transparency**: When an SQL error occurs, `VaultException` must preserve the original `SQLException` as its cause (`new VaultException("...", sqlException)`).
4. **Auto-Generated Key Retrieval**: Inserting a document, chunk, or chat message must return the auto-incremented primary key ID from SQLite using `Statement.RETURN_GENERATED_KEYS`.
5. **Resource Safety**: All `Connection`, `PreparedStatement`, and `ResultSet` instances must be closed automatically via try-with-resources even if an error is thrown.

---

### Task 1: Maven Project Scaffolding & Package Tree

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/cortexgraph/parser/package-info.java`
- Create: `src/main/java/com/cortexgraph/rag/package-info.java`
- Create: `src/main/java/com/cortexgraph/ui/package-info.java`
- Test: `src/test/java/com/cortexgraph/ProjectStructureTest.java`

**Interfaces:**
- Produces: Base Maven configuration and all designated package directories (`com.cortexgraph.model`, `com.cortexgraph.parser`, `com.cortexgraph.rag`, `com.cortexgraph.dao`, `com.cortexgraph.exception`, `com.cortexgraph.ui`).

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectStructureTest {
    @Test
    void testPackageExistence() {
        assertTrue(Package.getPackage("com.cortexgraph.parser") != null || 
                   getClass().getClassLoader().getResource("com/cortexgraph/parser") != null);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ProjectStructureTest`
Expected: FAIL (project/POM does not exist yet)

- [ ] **Step 3: Implement `pom.xml` and package-info placeholders**

Configure `pom.xml` with:
- Group ID: `com.cortexgraph`, Artifact ID: `cortexgraph`, Version: `1.0.0-SNAPSHOT`
- Java 21 release property
- Dependencies: `javafx-controls` (21.0.2), `pdfbox` (3.0.3), `sqlite-jdbc` (3.45.2.0), `langchain4j` (0.35.0), and `junit-jupiter` (5.10.2, test scope).
- Create directories and placeholder `package-info.java` files for `com.cortexgraph.parser`, `com.cortexgraph.rag`, and `com.cortexgraph.ui`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/
git commit -m "chore: scaffold Maven Java 21 project and package structure"
```

---

### Task 2: Custom Checked Exceptions

**Files:**
- Create: `src/main/java/com/cortexgraph/exception/DocumentParseException.java`
- Create: `src/main/java/com/cortexgraph/exception/VaultException.java`
- Test: `src/test/java/com/cortexgraph/exception/ExceptionTest.java`

**Interfaces:**
- Produces:
  - `com.cortexgraph.exception.DocumentParseException extends Exception`
  - `com.cortexgraph.exception.VaultException extends Exception`
  - Constructors: `(String message)`, `(String message, Throwable cause)`, `(Throwable cause)`

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ExceptionTest {
    @Test
    void testDocumentParseExceptionConstructors() {
        Throwable cause = new RuntimeException("Underlying I/O issue");
        DocumentParseException ex = new DocumentParseException("Parsing failed", cause);
        assertEquals("Parsing failed", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertTrue(ex instanceof Exception);
    }

    @Test
    void testVaultExceptionConstructors() {
        Throwable cause = new RuntimeException("DB down");
        VaultException ex = new VaultException("Database error", cause);
        assertEquals("Database error", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertTrue(ex instanceof Exception);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ExceptionTest`
Expected: Compilation failure (cannot find symbol `DocumentParseException`, `VaultException`)

- [ ] **Step 3: Implement `DocumentParseException` and `VaultException`**

Write minimal checked exception classes extending `java.lang.Exception` with constructors for message, message + cause, and cause only.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ExceptionTest`
Expected: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/exception/ src/test/java/com/cortexgraph/exception/
git commit -m "feat: add DocumentParseException and VaultException checked exceptions"
```

---

### Task 3: Domain Model Classes

**Files:**
- Create: `src/main/java/com/cortexgraph/model/DocumentRecord.java`
- Create: `src/main/java/com/cortexgraph/model/TextChunk.java`
- Create: `src/main/java/com/cortexgraph/model/ChatMessage.java`
- Test: `src/test/java/com/cortexgraph/model/ModelTest.java`

**Interfaces:**
- Produces:
  - `DocumentRecord(int id, String filename, String filePath, long fileSize, String createdAt)` + constructor `(String filename, String filePath, long fileSize)`
  - `TextChunk(int id, int documentId, int chunkIndex, String content)` + constructor `(int documentId, int chunkIndex, String content)`
  - `ChatMessage(int id, String sender, String content, String timestamp)` + constructor `(String sender, String content)`

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ModelTest {
    @Test
    void testDocumentRecord() {
        DocumentRecord doc = new DocumentRecord("sample.pdf", "/docs/sample.pdf", 1024L);
        assertEquals("sample.pdf", doc.getFilename());
        assertEquals("/docs/sample.pdf", doc.getFilePath());
        assertEquals(1024L, doc.getFileSize());
        assertEquals(0, doc.getId());

        DocumentRecord persistedDoc = new DocumentRecord(1, "sample.pdf", "/docs/sample.pdf", 1024L, "2026-11-22 10:00:00");
        assertEquals(1, persistedDoc.getId());
        assertEquals("2026-11-22 10:00:00", persistedDoc.getCreatedAt());
    }

    @Test
    void testTextChunk() {
        TextChunk chunk = new TextChunk(1, 0, "Introduction text");
        assertEquals(1, chunk.getDocumentId());
        assertEquals(0, chunk.getChunkIndex());
        assertEquals("Introduction text", chunk.getContent());

        TextChunk persistedChunk = new TextChunk(42, 1, 0, "Introduction text");
        assertEquals(42, persistedChunk.getId());
    }

    @Test
    void testChatMessage() {
        ChatMessage msg = new ChatMessage("user", "Hello world");
        assertEquals("user", msg.getSender());
        assertEquals("Hello world", msg.getContent());

        ChatMessage persistedMsg = new ChatMessage(99, "assistant", "Hi there", "2026-11-22 10:05:00");
        assertEquals(99, persistedMsg.getId());
        assertEquals("2026-11-22 10:05:00", persistedMsg.getTimestamp());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ModelTest`
Expected: Compilation failure (cannot find symbols `DocumentRecord`, `TextChunk`, `ChatMessage`)

- [ ] **Step 3: Implement `DocumentRecord`, `TextChunk`, and `ChatMessage`**

Create readable, concise POJO classes with:
- Private final fields
- Dual constructors (new entity vs. entity loaded from DB with id/timestamp)
- Standard getters, `toString()`, `equals()`, and `hashCode()`

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ModelTest`
Expected: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/model/ src/test/java/com/cortexgraph/model/
git commit -m "feat: implement DocumentRecord, TextChunk, and ChatMessage models"
```

---

### Task 4: DatabaseManager & SQLite Schema Initialization

**Files:**
- Create: `src/main/java/com/cortexgraph/dao/DatabaseManager.java`
- Test: `src/test/java/com/cortexgraph/dao/DatabaseManagerTest.java`

**Interfaces:**
- Consumes: `VaultException` from `com.cortexgraph.exception`
- Produces:
  - `DatabaseManager.DEFAULT_DB_URL = "jdbc:sqlite:cortexgraph.db"`
  - `public static Connection getConnection(String dbUrl) throws VaultException`
  - `public static Connection getConnection() throws VaultException`
  - `public static void initializeDatabase(String dbUrl) throws VaultException`
  - `public static void initializeDatabase() throws VaultException`

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph.dao;

import com.cortexgraph.exception.VaultException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseManagerTest {
    @Test
    void testInitializeDatabaseCreatesTables(@TempDir Path tempDir) throws Exception {
        String testDb = "jdbc:sqlite:" + tempDir.resolve("test_cortex.db");
        DatabaseManager.initializeDatabase(testDb);

        // Verify tables exist
        try (Connection conn = DatabaseManager.getConnection(testDb);
             Statement stmt = conn.createStatement()) {
            
            // documents table
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='documents'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
            // chunks table
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='chunks'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
            // chat_history table
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='chat_history'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }

        // Idempotency check: running initialize again shouldn't fail
        assertDoesNotThrow(() -> DatabaseManager.initializeDatabase(testDb));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DatabaseManagerTest`
Expected: Compilation failure (cannot find symbol `DatabaseManager`)

- [ ] **Step 3: Implement `DatabaseManager`**

Implement `DatabaseManager`:
- Default URL: `jdbc:sqlite:cortexgraph.db`
- `getConnection(String dbUrl)`: opens connection using `DriverManager.getConnection(dbUrl)` and executes `PRAGMA foreign_keys = ON;` to enforce foreign keys. Catches `SQLException` and throws `VaultException`.
- `initializeDatabase(String dbUrl)`: executes DDL statements with try-with-resources:
  - `CREATE TABLE IF NOT EXISTS documents (id INTEGER PRIMARY KEY AUTOINCREMENT, filename TEXT NOT NULL, file_path TEXT NOT NULL, file_size INTEGER NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);`
  - `CREATE TABLE IF NOT EXISTS chunks (id INTEGER PRIMARY KEY AUTOINCREMENT, document_id INTEGER NOT NULL, chunk_index INTEGER NOT NULL, content TEXT NOT NULL, FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE);`
  - `CREATE TABLE IF NOT EXISTS chat_history (id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT NOT NULL, content TEXT NOT NULL, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP);`

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DatabaseManagerTest`
Expected: Tests run: 1, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/dao/DatabaseManager.java src/test/java/com/cortexgraph/dao/DatabaseManagerTest.java
git commit -m "feat: implement DatabaseManager with SQLite schema initialization"
```

---

### Task 5: DocumentDAO

**Files:**
- Create: `src/main/java/com/cortexgraph/dao/DocumentDAO.java`
- Test: `src/test/java/com/cortexgraph/dao/DocumentDAOTest.java`

**Interfaces:**
- Consumes: `DocumentRecord`, `VaultException`, `DatabaseManager`
- Produces:
  - `DocumentDAO(String dbUrl)` and default `DocumentDAO()`
  - `int save(DocumentRecord doc) throws VaultException`
  - `Optional<DocumentRecord> findById(int id) throws VaultException`
  - `List<DocumentRecord> findAll() throws VaultException`
  - `boolean delete(int id) throws VaultException`

- [ ] **Step 1: Write the failing test**

```java
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
        DocumentRecord doc = new DocumentRecord("architecture.pdf", "/vault/architecture.pdf", 2048L);
        int generatedId = dao.save(doc);
        assertTrue(generatedId > 0);

        Optional<DocumentRecord> fetched = dao.findById(generatedId);
        assertTrue(fetched.isPresent());
        assertEquals("architecture.pdf", fetched.get().getFilename());
        assertEquals(2048L, fetched.get().getFileSize());
        assertNotNull(fetched.get().getCreatedAt());

        List<DocumentRecord> all = dao.findAll();
        assertEquals(1, all.size());

        boolean deleted = dao.delete(generatedId);
        assertTrue(deleted);
        assertTrue(dao.findById(generatedId).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DocumentDAOTest`
Expected: Compilation failure (cannot find symbol `DocumentDAO`)

- [ ] **Step 3: Implement `DocumentDAO`**

Implement `DocumentDAO` using `PreparedStatement` and try-with-resources:
- `save`: `INSERT INTO documents (filename, file_path, file_size) VALUES (?, ?, ?)` with `Statement.RETURN_GENERATED_KEYS`.
- `findById`: `SELECT id, filename, file_path, file_size, created_at FROM documents WHERE id = ?`.
- `findAll`: `SELECT id, filename, file_path, file_size, created_at FROM documents ORDER BY id DESC`.
- `delete`: `DELETE FROM documents WHERE id = ?`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DocumentDAOTest`
Expected: Tests run: 1, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/dao/DocumentDAO.java src/test/java/com/cortexgraph/dao/DocumentDAOTest.java
git commit -m "feat: implement DocumentDAO with CRUD operations"
```

---

### Task 6: ChunkDAO

**Files:**
- Create: `src/main/java/com/cortexgraph/dao/ChunkDAO.java`
- Test: `src/test/java/com/cortexgraph/dao/ChunkDAOTest.java`

**Interfaces:**
- Consumes: `TextChunk`, `VaultException`, `DatabaseManager`
- Produces:
  - `ChunkDAO(String dbUrl)` and default `ChunkDAO()`
  - `int save(TextChunk chunk) throws VaultException`
  - `void saveAll(List<TextChunk> chunks) throws VaultException`
  - `List<TextChunk> findByDocumentId(int documentId) throws VaultException`
  - `boolean deleteByDocumentId(int documentId) throws VaultException`

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph.dao;

import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.TextChunk;
import com.cortexgraph.exception.VaultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkDAOTest {
    private ChunkDAO chunkDAO;
    private DocumentDAO docDAO;
    private int docId;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("chunk_test.db");
        DatabaseManager.initializeDatabase(dbUrl);
        docDAO = new DocumentDAO(dbUrl);
        chunkDAO = new ChunkDAO(dbUrl);
        docId = docDAO.save(new DocumentRecord("manual.pdf", "/docs/manual.pdf", 4096L));
    }

    @Test
    void testSaveAndFindByDocumentId() throws Exception {
        TextChunk c1 = new TextChunk(docId, 0, "First paragraph");
        TextChunk c2 = new TextChunk(docId, 1, "Second paragraph");
        chunkDAO.saveAll(List.of(c1, c2));

        List<TextChunk> chunks = chunkDAO.findByDocumentId(docId);
        assertEquals(2, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals("First paragraph", chunks.get(0).getContent());
        assertEquals(1, chunks.get(1).getChunkIndex());
    }

    @Test
    void testForeignConstraintFailureOnOrphanChunk() {
        TextChunk orphan = new TextChunk(99999, 0, "Invalid document reference");
        assertThrows(VaultException.class, () -> chunkDAO.save(orphan));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ChunkDAOTest`
Expected: Compilation failure (cannot find symbol `ChunkDAO`)

- [ ] **Step 3: Implement `ChunkDAO`**

Implement `ChunkDAO`:
- `save`: `INSERT INTO chunks (document_id, chunk_index, content) VALUES (?, ?, ?)` with `Statement.RETURN_GENERATED_KEYS`.
- `saveAll`: batch insert using `PreparedStatement.addBatch()` and `executeBatch()` inside a transaction (`conn.setAutoCommit(false)`).
- `findByDocumentId`: `SELECT id, document_id, chunk_index, content FROM chunks WHERE document_id = ? ORDER BY chunk_index ASC`.
- `deleteByDocumentId`: `DELETE FROM chunks WHERE document_id = ?`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ChunkDAOTest`
Expected: Tests run: 2, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/dao/ChunkDAO.java src/test/java/com/cortexgraph/dao/ChunkDAOTest.java
git commit -m "feat: implement ChunkDAO with batch insert and foreign key protection"
```

---

### Task 7: ChatDAO

**Files:**
- Create: `src/main/java/com/cortexgraph/dao/ChatDAO.java`
- Test: `src/test/java/com/cortexgraph/dao/ChatDAOTest.java`

**Interfaces:**
- Consumes: `ChatMessage`, `VaultException`, `DatabaseManager`
- Produces:
  - `ChatDAO(String dbUrl)` and default `ChatDAO()`
  - `int save(ChatMessage message) throws VaultException`
  - `List<ChatMessage> findAll() throws VaultException`
  - `void clearHistory() throws VaultException`

- [ ] **Step 1: Write the failing test**

```java
package com.cortexgraph.dao;

import com.cortexgraph.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChatDAOTest {
    private ChatDAO chatDAO;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("chat_test.db");
        DatabaseManager.initializeDatabase(dbUrl);
        chatDAO = new ChatDAO(dbUrl);
    }

    @Test
    void testSaveFindAndClearMessages() throws Exception {
        int id1 = chatDAO.save(new ChatMessage("user", "What is CortexGraph?"));
        int id2 = chatDAO.save(new ChatMessage("assistant", "It is a knowledge vault!"));
        assertTrue(id1 > 0);
        assertTrue(id2 > id1);

        List<ChatMessage> history = chatDAO.findAll();
        assertEquals(2, history.size());
        assertEquals("user", history.get(0).getSender());
        assertEquals("What is CortexGraph?", history.get(0).getContent());
        assertNotNull(history.get(0).getTimestamp());

        chatDAO.clearHistory();
        assertTrue(chatDAO.findAll().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ChatDAOTest`
Expected: Compilation failure (cannot find symbol `ChatDAO`)

- [ ] **Step 3: Implement `ChatDAO`**

Implement `ChatDAO`:
- `save`: `INSERT INTO chat_history (sender, content) VALUES (?, ?)` with `Statement.RETURN_GENERATED_KEYS`.
- `findAll`: `SELECT id, sender, content, timestamp FROM chat_history ORDER BY id ASC`.
- `clearHistory`: `DELETE FROM chat_history`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ChatDAOTest`
Expected: Tests run: 1, Failures: 0, Errors: 0. BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cortexgraph/dao/ChatDAO.java src/test/java/com/cortexgraph/dao/ChatDAOTest.java
git commit -m "feat: implement ChatDAO with history tracking and cleanup"
```

---

### Task 8: End-to-End Verification & Sanity Check

**Files:**
- Create: `src/test/java/com/cortexgraph/EndToEndIntegrationTest.java`

- [ ] **Step 1: Write integration test exercising full document lifecycle and cascading delete**

```java
package com.cortexgraph;

import com.cortexgraph.dao.*;
import com.cortexgraph.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EndToEndIntegrationTest {
    @Test
    void testFullWorkflowWithCascadingDeletes(@TempDir Path tempDir) throws Exception {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("e2e_cortex.db");
        DatabaseManager.initializeDatabase(dbUrl);

        DocumentDAO docDAO = new DocumentDAO(dbUrl);
        ChunkDAO chunkDAO = new ChunkDAO(dbUrl);
        ChatDAO chatDAO = new ChatDAO(dbUrl);

        // 1. Insert document
        int docId = docDAO.save(new DocumentRecord("whitepaper.pdf", "/data/whitepaper.pdf", 8192L));
        assertTrue(docId > 0);

        // 2. Insert chunks
        chunkDAO.saveAll(List.of(
            new TextChunk(docId, 0, "Abstract section"),
            new TextChunk(docId, 1, "Methodology section")
        ));
        assertEquals(2, chunkDAO.findByDocumentId(docId).size());

        // 3. Log chat interaction
        chatDAO.save(new ChatMessage("user", "Summarize abstract"));
        chatDAO.save(new ChatMessage("assistant", "The paper proposes..."));
        assertEquals(2, chatDAO.findAll().size());

        // 4. Delete document and verify cascading chunk deletion
        docDAO.delete(docId);
        assertTrue(chunkDAO.findByDocumentId(docId).isEmpty());
    }
}
```

- [ ] **Step 2: Run all tests in the project**

Run: `mvn clean test`
Expected: BUILD SUCCESS with all test suites passing

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/cortexgraph/EndToEndIntegrationTest.java
git commit -m "test: add end-to-end integration test verifying full data layer workflow"
```

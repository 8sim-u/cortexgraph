package com.cortexgraph.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ExceptionTest {
    @Test
    void testDocumentParseExceptionConstructors() {
        DocumentParseException exMsg = new DocumentParseException("Parsing error");
        assertEquals("Parsing error", exMsg.getMessage());

        Throwable cause = new RuntimeException("Underlying I/O issue");
        DocumentParseException exBoth = new DocumentParseException("Parsing failed", cause);
        assertEquals("Parsing failed", exBoth.getMessage());
        assertSame(cause, exBoth.getCause());

        DocumentParseException exCause = new DocumentParseException(cause);
        assertSame(cause, exCause.getCause());
        assertTrue(exMsg instanceof Exception);
    }

    @Test
    void testVaultExceptionConstructors() {
        VaultException exMsg = new VaultException("Database error");
        assertEquals("Database error", exMsg.getMessage());

        Throwable cause = new RuntimeException("DB down");
        VaultException exBoth = new VaultException("Database error", cause);
        assertEquals("Database error", exBoth.getMessage());
        assertSame(cause, exBoth.getCause());

        VaultException exCause = new VaultException(cause);
        assertSame(cause, exCause.getCause());
        assertTrue(exMsg instanceof Exception);
    }
}

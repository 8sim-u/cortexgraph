package com.cortexgraph.exception;

/**
 * Checked exception thrown when document parsing fails (e.g. invalid PDF format, corrupted files).
 */
public class DocumentParseException extends Exception {

    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public DocumentParseException(Throwable cause) {
        super(cause);
    }
}

package com.cortexgraph.exception;

/**
 * Checked exception thrown when database or vault storage operations fail.
 */
public class VaultException extends Exception {

    public VaultException(String message) {
        super(message);
    }

    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }

    public VaultException(Throwable cause) {
        super(cause);
    }
}

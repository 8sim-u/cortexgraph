package com.cortexgraph.parser;

import com.cortexgraph.exception.DocumentParseException;
import com.cortexgraph.model.ParsedDocument;
import com.cortexgraph.model.TextChunk;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Handles asynchronous parsing and chunking of documents off the UI thread
 * using a dedicated ExecutorService thread pool.
 */
public class DocumentIngestor implements AutoCloseable {

    private final ExecutorService executor;
    private final TextChunker chunker;
    private final TextDocumentParser textParser;
    private final PdfDocumentParser pdfParser;

    /**
     * Creates an ingestor with a thread pool sized to available CPU cores.
     */
    public DocumentIngestor() {
        this(Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Creates an ingestor with a specified thread pool size.
     */
    public DocumentIngestor(int threadPoolSize) {
        this(Executors.newFixedThreadPool(threadPoolSize), new TextChunker());
    }

    /**
     * Full constructor allowing custom ExecutorService and TextChunker.
     */
    public DocumentIngestor(ExecutorService executor, TextChunker chunker) {
        this.executor = executor;
        this.chunker = chunker != null ? chunker : new TextChunker();
        this.textParser = new TextDocumentParser();
        this.pdfParser = new PdfDocumentParser();
    }

    /**
     * Resolves the appropriate DocumentParser based on file extension.
     */
    public DocumentParser getParserFor(String filename) throws DocumentParseException {
        if (filename == null || filename.isBlank()) {
            throw new DocumentParseException("Filename cannot be null or blank");
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return pdfParser;
        } else if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return textParser;
        } else {
            throw new DocumentParseException("Unsupported file extension for ingestion: " + filename + " (Supported: .pdf, .txt, .md)");
        }
    }

    /**
     * Asynchronously parses and chunks a document file on the thread pool.
     */
    public CompletableFuture<List<TextChunk>> ingestAsync(int documentId, File file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (file == null) {
                    throw new DocumentParseException("File cannot be null");
                }
                DocumentParser parser = getParserFor(file.getName());
                ParsedDocument parsedDoc = parser.parse(file);
                return chunker.chunk(documentId, parsedDoc);
            } catch (DocumentParseException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Asynchronously parses and chunks a document input stream on the thread pool.
     */
    public CompletableFuture<List<TextChunk>> ingestAsync(int documentId, InputStream inputStream, String filename) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DocumentParser parser = getParserFor(filename);
                ParsedDocument parsedDoc = parser.parse(inputStream, filename);
                return chunker.chunk(documentId, parsedDoc);
            } catch (DocumentParseException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Initiates an orderly shutdown of the thread pool.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Attempts to stop all actively executing tasks immediately.
     */
    public List<Runnable> shutdownNow() {
        return executor.shutdownNow();
    }

    /**
     * Returns true if this ingestor's thread pool has been shut down.
     */
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    /**
     * Blocks until all tasks have completed execution after a shutdown request, or the timeout occurs.
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public void close() {
        shutdown();
        try {
            if (!awaitTermination(2, TimeUnit.SECONDS)) {
                shutdownNow();
            }
        } catch (InterruptedException e) {
            shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

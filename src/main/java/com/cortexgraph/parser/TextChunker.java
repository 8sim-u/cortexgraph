package com.cortexgraph.parser;

import com.cortexgraph.exception.DocumentParseException;
import com.cortexgraph.model.ParsedDocument;
import com.cortexgraph.model.ParsedPage;
import com.cortexgraph.model.TextChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits extracted document text into overlapping word-based chunks while preserving
 * sequential chunk indices and source page numbers.
 */
public class TextChunker {

    public static final int DEFAULT_CHUNK_SIZE = 250;
    public static final int DEFAULT_OVERLAP = 40;

    private final int chunkSize;
    private final int overlap;

    /**
     * Default constructor configured for ~250 words per chunk and ~40 words overlap.
     */
    public TextChunker() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * Parameterized constructor allowing custom chunk size and overlap.
     */
    public TextChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than 0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be >= 0 and < chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getOverlap() {
        return overlap;
    }

    /**
     * Chunks a ParsedDocument into a list of TextChunks.
     *
     * @param documentId the database ID of the document being chunked
     * @param parsedDocument the parsed document structure
     * @return a list of overlapping TextChunk objects
     * @throws DocumentParseException if the document is null, has no pages, or content is empty
     */
    public List<TextChunk> chunk(int documentId, ParsedDocument parsedDocument) throws DocumentParseException {
        if (parsedDocument == null || parsedDocument.getPages().isEmpty()) {
            throw new DocumentParseException("Cannot chunk document: document is null or contains no pages");
        }

        List<TextChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int step = chunkSize - overlap;

        for (ParsedPage page : parsedDocument.getPages()) {
            String content = page.getContent() != null ? page.getContent().trim() : "";
            if (content.isEmpty()) {
                continue;
            }

            String[] words = content.split("\\p{javaWhitespace}+");
            if (words.length == 0 || (words.length == 1 && words[0].isEmpty())) {
                continue;
            }

            for (int start = 0; start < words.length; start += step) {
                int end = Math.min(start + chunkSize, words.length);

                StringBuilder chunkText = new StringBuilder();
                for (int i = start; i < end; i++) {
                    chunkText.append(words[i]);
                    if (i < end - 1) {
                        chunkText.append(" ");
                    }
                }

                chunks.add(new TextChunk(0, documentId, chunkIndex++, chunkText.toString(), page.getPageNumber()));

                if (end >= words.length) {
                    break;
                }
            }
        }

        if (chunks.isEmpty()) {
            throw new DocumentParseException("Document contains no readable text to chunk: " + parsedDocument.getFilename());
        }

        return chunks;
    }
}

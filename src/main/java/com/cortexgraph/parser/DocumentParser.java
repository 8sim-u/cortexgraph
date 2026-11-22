package com.cortexgraph.parser;

import com.cortexgraph.exception.DocumentParseException;
import com.cortexgraph.model.ParsedDocument;

import java.io.File;
import java.io.InputStream;

/**
 * Common interface for document parsers.
 */
public interface DocumentParser {

    /**
     * Parses the specified file into a ParsedDocument.
     *
     * @param file the file to parse
     * @return the structured ParsedDocument
     * @throws DocumentParseException if reading fails, file is corrupt, or content is empty
     */
    ParsedDocument parse(File file) throws DocumentParseException;

    /**
     * Parses content from an input stream with a given filename/identifier.
     *
     * @param inputStream the stream to read from
     * @param filename the source filename or label
     * @return the structured ParsedDocument
     * @throws DocumentParseException if reading fails, stream is corrupt, or content is empty
     */
    ParsedDocument parse(InputStream inputStream, String filename) throws DocumentParseException;
}

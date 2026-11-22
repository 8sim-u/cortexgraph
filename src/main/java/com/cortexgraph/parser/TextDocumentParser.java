package com.cortexgraph.parser;

import com.cortexgraph.exception.DocumentParseException;
import com.cortexgraph.model.ParsedDocument;
import com.cortexgraph.model.ParsedPage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Parses plain text (.txt) and Markdown (.md) documents using BufferedReader and InputStreamReader.
 */
public class TextDocumentParser implements DocumentParser {

    @Override
    public ParsedDocument parse(File file) throws DocumentParseException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new DocumentParseException("File does not exist or is not a valid regular file: " + (file != null ? file.getPath() : "null"));
        }
        try (InputStream fis = new FileInputStream(file)) {
            return parse(fis, file.getName());
        } catch (IOException e) {
            throw new DocumentParseException("Failed to open text file: " + file.getPath(), e);
        }
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String filename) throws DocumentParseException {
        if (inputStream == null) {
            throw new DocumentParseException("InputStream cannot be null for file: " + filename);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }

            String content = sb.toString().trim();
            if (content.isEmpty()) {
                throw new DocumentParseException("File content is empty: " + filename);
            }

            ParsedPage singlePage = new ParsedPage(1, content);
            return new ParsedDocument(filename, List.of(singlePage));
        } catch (IOException e) {
            throw new DocumentParseException("Error reading text stream for: " + filename, e);
        }
    }
}

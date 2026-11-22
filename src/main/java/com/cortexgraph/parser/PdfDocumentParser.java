package com.cortexgraph.parser;

import com.cortexgraph.exception.DocumentParseException;
import com.cortexgraph.model.ParsedDocument;
import com.cortexgraph.model.ParsedPage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses PDF documents using Apache PDFBox Loader and extracts text page by page.
 */
public class PdfDocumentParser implements DocumentParser {

    @Override
    public ParsedDocument parse(File file) throws DocumentParseException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new DocumentParseException("File does not exist or is not a regular file: " + (file != null ? file.getPath() : "null"));
        }
        try (InputStream fis = new BufferedInputStream(new FileInputStream(file))) {
            return parse(fis, file.getName());
        } catch (IOException e) {
            throw new DocumentParseException("Failed to read PDF file: " + file.getPath(), e);
        }
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String filename) throws DocumentParseException {
        if (inputStream == null) {
            throw new DocumentParseException("InputStream cannot be null for PDF: " + filename);
        }

        BufferedInputStream bis = (inputStream instanceof BufferedInputStream)
                ? (BufferedInputStream) inputStream
                : new BufferedInputStream(inputStream);

        try {
            byte[] pdfBytes = bis.readAllBytes();
            if (pdfBytes.length == 0) {
                throw new DocumentParseException("PDF file is empty (0 bytes): " + filename);
            }

            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                int totalPages = document.getNumberOfPages();
                if (totalPages == 0) {
                    throw new DocumentParseException("PDF document contains 0 pages: " + filename);
                }

                PDFTextStripper stripper = new PDFTextStripper();
                List<ParsedPage> pages = new ArrayList<>();
                boolean hasAnyText = false;

                for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                    stripper.setStartPage(pageNum);
                    stripper.setEndPage(pageNum);
                    String pageText = stripper.getText(document).trim();
                    if (!pageText.isEmpty()) {
                        hasAnyText = true;
                    }
                    pages.add(new ParsedPage(pageNum, pageText));
                }

                if (!hasAnyText) {
                    throw new DocumentParseException("PDF document contains no readable text: " + filename);
                }

                return new ParsedDocument(filename, pages);
            }
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse PDF document: " + filename, e);
        }
    }
}

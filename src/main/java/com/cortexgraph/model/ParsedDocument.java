package com.cortexgraph.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a document parsed into structured pages.
 */
public class ParsedDocument {
    private final String filename;
    private final List<ParsedPage> pages;

    public ParsedDocument(String filename, List<ParsedPage> pages) {
        this.filename = filename != null ? filename : "";
        this.pages = pages != null ? List.copyOf(pages) : List.of();
    }

    public String getFilename() {
        return filename;
    }

    public List<ParsedPage> getPages() {
        return pages;
    }

    /**
     * Joins all pages with double newlines.
     */
    public String getFullText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            sb.append(pages.get(i).getContent());
            if (i < pages.size() - 1) {
                sb.append("\n\n");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParsedDocument that = (ParsedDocument) o;
        return Objects.equals(filename, that.filename) && Objects.equals(pages, that.pages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filename, pages);
    }

    @Override
    public String toString() {
        return "ParsedDocument{" +
                "filename='" + filename + '\'' +
                ", pageCount=" + pages.size() +
                '}';
    }
}

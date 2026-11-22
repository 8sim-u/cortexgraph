package com.cortexgraph.model;

import java.util.Objects;

/**
 * Represents a parsed page or section of a document.
 */
public class ParsedPage {
    private final int pageNumber;
    private final String content;

    public ParsedPage(int pageNumber, String content) {
        this.pageNumber = pageNumber;
        this.content = content != null ? content : "";
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getContent() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParsedPage that = (ParsedPage) o;
        return pageNumber == that.pageNumber && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageNumber, content);
    }

    @Override
    public String toString() {
        return "ParsedPage{" +
                "pageNumber=" + pageNumber +
                ", contentLength=" + content.length() +
                '}';
    }
}

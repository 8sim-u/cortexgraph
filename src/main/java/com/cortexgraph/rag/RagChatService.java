package com.cortexgraph.rag;

import com.cortexgraph.dao.ChatDAO;
import com.cortexgraph.dao.ChunkDAO;
import com.cortexgraph.dao.DocumentDAO;
import com.cortexgraph.exception.VaultException;
import com.cortexgraph.model.ChatMessage;
import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.ScoredChunk;
import com.cortexgraph.model.TextChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for grounded Retrieval-Augmented Generation (RAG) chat interactions with source citations.
 */
public class RagChatService {
    private final VectorSearchService searchService;
    private final EmbeddingService embeddingService;
    private final DocumentDAO documentDAO;
    private final ChunkDAO chunkDAO;
    private final ChatDAO chatDAO;

    public RagChatService(VectorSearchService searchService,
                          EmbeddingService embeddingService,
                          DocumentDAO documentDAO,
                          ChunkDAO chunkDAO,
                          ChatDAO chatDAO) {
        if (searchService == null || embeddingService == null ||
            documentDAO == null || chunkDAO == null || chatDAO == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.searchService = searchService;
        this.embeddingService = embeddingService;
        this.documentDAO = documentDAO;
        this.chunkDAO = chunkDAO;
        this.chatDAO = chatDAO;
    }

    /**
     * Answers a user question using grounded nearest-chunk retrieval with [Filename, Page X] citations.
     */
    public ChatMessage askQuestion(String question, int k) throws VaultException {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question cannot be blank");
        }
        if (k <= 0) {
            k = 3;
        }

        // 1. Record user question in chat history
        chatDAO.save(new ChatMessage("user", question.trim()));

        // 2. Fetch all indexed documents and chunks
        List<DocumentRecord> documents = documentDAO.findAll();
        if (documents.isEmpty()) {
            String fallback = "I could not find any indexed documents to answer your question. Please ingest documents first.";
            ChatMessage assistantMsg = new ChatMessage("assistant", fallback);
            chatDAO.save(assistantMsg);
            return assistantMsg;
        }

        Map<Integer, DocumentRecord> docMap = new HashMap<>();
        List<TextChunk> allChunks = new ArrayList<>();
        for (DocumentRecord doc : documents) {
            docMap.put(doc.getId(), doc);
            allChunks.addAll(chunkDAO.findByDocumentId(doc.getId()));
        }

        if (allChunks.isEmpty()) {
            String fallback = "I could not find any text chunks in the indexed documents.";
            ChatMessage assistantMsg = new ChatMessage("assistant", fallback);
            chatDAO.save(assistantMsg);
            return assistantMsg;
        }

        // 3. Embed question and perform top-K search
        double[] queryVec = embeddingService.embedText(question);
        List<ScoredChunk> topChunks = searchService.topKChunks(queryVec, allChunks, embeddingService, k);

        if (topChunks.isEmpty()) {
            String fallback = "I could not find relevant information in the ingested documents.";
            ChatMessage assistantMsg = new ChatMessage("assistant", fallback);
            chatDAO.save(assistantMsg);
            return assistantMsg;
        }

        // 4. Format grounded response with citations
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("Based on the indexed documents, here is what I found:\n\n");

        for (ScoredChunk scored : topChunks) {
            TextChunk chunk = scored.getChunk();
            DocumentRecord doc = docMap.get(chunk.getDocumentId());
            String filename = doc != null ? doc.getFilename() : ("document_" + chunk.getDocumentId());
            int page = chunk.getPageNumber() > 0 ? chunk.getPageNumber() : (chunk.getChunkIndex() + 1);

            responseBuilder.append("• [").append(filename).append(", Page ").append(page).append("] ")
                    .append("(relevance: ").append(String.format("%.1f%%", Math.max(0.0, scored.getScore()) * 100)).append(")\n")
                    .append("  \"").append(chunk.getContent().trim()).append("\"\n\n");
        }

        ChatMessage assistantMsg = new ChatMessage("assistant", responseBuilder.toString().trim());
        chatDAO.save(assistantMsg);
        return assistantMsg;
    }

    public List<ChatMessage> getChatHistory() throws VaultException {
        return chatDAO.findAll();
    }

    public void clearChatHistory() throws VaultException {
        chatDAO.clear();
    }
}

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
import java.util.Collections;
import java.util.Comparator;
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

        // 3. Extract query keywords and phrase
        List<String> keywords = extractKeywords(question);
        String queryPhrase = String.join(" ", keywords);

        // 4. Retrieve and rank candidate chunks using hybrid vector similarity + keyword boosting
        double[] queryVec = embeddingService.embedText(question);
        List<ScoredChunk> topChunks = rankChunks(queryVec, allChunks, keywords, queryPhrase, k);

        if (topChunks.isEmpty()) {
            String fallback = "I could not find relevant information in the ingested documents.";
            ChatMessage assistantMsg = new ChatMessage("assistant", fallback);
            chatDAO.save(assistantMsg);
            return assistantMsg;
        }

        // 5. Synthesize concise answer above citations
        String conciseAnswer = synthesizeAnswer(topChunks, keywords);

        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("Answer:\n")
                .append(conciseAnswer)
                .append("\n\nSources & Citations:\n");

        for (ScoredChunk scored : topChunks) {
            TextChunk chunk = scored.getChunk();
            DocumentRecord doc = docMap.get(chunk.getDocumentId());
            String filename = doc != null ? doc.getFilename() : ("document_" + chunk.getDocumentId());
            int page = chunk.getPageNumber() > 0 ? chunk.getPageNumber() : (chunk.getChunkIndex() + 1);
            double relPercent = Math.min(100.0, Math.max(0.0, scored.getScore() > 1.0 ? 1.0 : scored.getScore()) * 100);

            responseBuilder.append("• [").append(filename).append(", Page ").append(page).append("] ")
                    .append("(relevance: ").append(String.format("%.1f%%", relPercent)).append(")\n")
                    .append("  \"").append(chunk.getContent().trim()).append("\"\n\n");
        }

        ChatMessage assistantMsg = new ChatMessage("assistant", responseBuilder.toString().trim());
        chatDAO.save(assistantMsg);
        return assistantMsg;
    }

    /**
     * Extracts non-stopword keywords from text.
     */
    public List<String> extractKeywords(String text) {
        if (text == null) return Collections.emptyList();
        String[] tokens = text.toLowerCase().split("\\p{javaWhitespace}+");
        List<String> keywords = new ArrayList<>();
        for (String raw : tokens) {
            String token = raw.replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "");
            if (!token.isEmpty() && !DeterministicEmbeddingModel.STOPWORDS.contains(token)) {
                keywords.add(token);
            }
        }
        return keywords;
    }

    /**
     * Ranks chunks by combining cosine vector similarity with exact query keyword and phrase matching boosts.
     */
    private List<ScoredChunk> rankChunks(double[] queryVector, List<TextChunk> candidateChunks,
                                         List<String> keywords, String queryPhrase, int k) {
        List<ScoredChunk> scoredList = new ArrayList<>();

        for (TextChunk chunk : candidateChunks) {
            if (chunk == null) continue;
            double[] chunkVec = embeddingService.embedChunk(chunk);
            double cosSim = VectorUtils.cosineSimilarity(queryVector, chunkVec);

            String content = chunk.getContent().toLowerCase();

            // Exact phrase match bonus (e.g. "degree sum property")
            double exactPhraseBonus = 0.0;
            if (!queryPhrase.isEmpty() && queryPhrase.contains(" ") && content.contains(queryPhrase)) {
                exactPhraseBonus = 0.60;
            }

            // Keyword overlap bonus
            double keywordBonus = 0.0;
            if (!keywords.isEmpty()) {
                int matches = 0;
                for (String kw : keywords) {
                    if (content.contains(kw)) {
                        matches++;
                    }
                }
                keywordBonus = 0.30 * ((double) matches / keywords.size());
                if (matches == keywords.size()) {
                    keywordBonus += 0.10; // Extra boost if all non-stopword terms matched
                }
            }

            double totalScore = cosSim + exactPhraseBonus + keywordBonus;
            scoredList.add(new ScoredChunk(chunk, totalScore));
        }

        scoredList.sort(Comparator.comparingDouble(ScoredChunk::getScore).reversed());
        if (scoredList.size() > k) {
            return new ArrayList<>(scoredList.subList(0, k));
        }
        return scoredList;
    }

    /**
     * Extracts a concise grounded answer from the top relevant chunks.
     */
    private String synthesizeAnswer(List<ScoredChunk> topChunks, List<String> keywords) {
        if (topChunks.isEmpty()) {
            return "No matching content found.";
        }

        TextChunk primaryChunk = topChunks.get(0).getChunk();
        String content = primaryChunk.getContent().trim();

        if (content.length() <= 300) {
            return content;
        }

        // Split into sentences
        String[] sentences = content.split("(?<=[.!?\\n])\\s+");
        if (sentences.length <= 1) {
            return content;
        }

        // Score sentences by keyword matches
        String bestSentence = sentences[0].trim();
        int maxMatches = -1;

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;
            String lower = trimmed.toLowerCase();
            int matches = 0;
            for (String kw : keywords) {
                if (lower.contains(kw)) {
                    matches++;
                }
            }
            if (matches > maxMatches) {
                maxMatches = matches;
                bestSentence = trimmed;
            }
        }

        return bestSentence;
    }

    public List<ChatMessage> getChatHistory() throws VaultException {
        return chatDAO.findAll();
    }

    public void clearChatHistory() throws VaultException {
        chatDAO.clear();
    }
}

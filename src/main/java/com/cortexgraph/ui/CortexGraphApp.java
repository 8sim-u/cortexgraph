package com.cortexgraph.ui;

import com.cortexgraph.dao.ChatDAO;
import com.cortexgraph.dao.ChunkDAO;
import com.cortexgraph.dao.DatabaseManager;
import com.cortexgraph.dao.DocumentDAO;
import com.cortexgraph.dao.EmbeddingDAO;
import com.cortexgraph.model.ChatMessage;
import com.cortexgraph.model.DocumentRecord;
import com.cortexgraph.model.GraphEdge;
import com.cortexgraph.model.TextChunk;
import com.cortexgraph.parser.DocumentIngestor;
import com.cortexgraph.rag.DeterministicEmbeddingModel;
import com.cortexgraph.rag.EmbeddingService;
import com.cortexgraph.rag.RagChatService;
import com.cortexgraph.rag.VectorSearchService;
import com.cortexgraph.ui.canvas.GraphCanvas;
import com.cortexgraph.ui.model.GraphNode;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main JavaFX Application for CortexGraph.
 * Provides a dark-mode 2D physics graph canvas, document ingestion, and grounded RAG chat.
 */
public class CortexGraphApp extends Application {

    private String dbUrl = DatabaseManager.DEFAULT_DB_URL;
    private DocumentDAO documentDAO;
    private ChunkDAO chunkDAO;
    private ChatDAO chatDAO;
    private EmbeddingDAO embeddingDAO;

    private DocumentIngestor ingestor;
    private EmbeddingService embeddingService;
    private VectorSearchService searchService;
    private RagChatService chatService;

    // Managed background executor with daemon worker threads
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "CortexGraph-Worker");
                t.setDaemon(true);
                return t;
            }
    );

    // UI Components
    private GraphCanvas graphCanvas;
    private ObservableList<String> documentsObservable;
    private ObservableList<String> chatObservable;
    private ListView<String> chatListView;

    private Label inspectorDocLabel;
    private Label inspectorPageLabel;
    private Label inspectorChunkIdLabel;
    private TextArea inspectorContentArea;

    private TextField chatInputField;
    private Button sendButton;
    private Button ingestButton;
    private Slider thresholdSlider;
    private Label statusLabel;

    public static void main(String[] args) {
        launch(args);
    }

    public void setDbUrl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    @Override
    public void init() throws Exception {
        DatabaseManager.initializeDatabase(dbUrl);

        documentDAO = new DocumentDAO(dbUrl);
        chunkDAO = new ChunkDAO(dbUrl);
        chatDAO = new ChatDAO(dbUrl);
        embeddingDAO = new EmbeddingDAO(dbUrl);

        ingestor = new DocumentIngestor();
        embeddingService = new EmbeddingService(new DeterministicEmbeddingModel(64));
        searchService = new VectorSearchService();
        chatService = new RagChatService(searchService, embeddingService, documentDAO, chunkDAO, chatDAO);

        // Load existing embeddings from SQLite into memory cache
        embeddingService.loadFromDatabase(embeddingDAO);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("CortexGraph — Neural Knowledge Graph & RAG Studio");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #11111B; -fx-text-fill: #CDD6F4;");

        // Top Toolbar
        ToolBar toolBar = createTopToolbar(primaryStage);
        root.setTop(toolBar);

        // Center: Graph Canvas
        Pane canvasContainer = new Pane();
        canvasContainer.setStyle("-fx-background-color: #1E1E2E;");
        graphCanvas = new GraphCanvas(800, 650);
        canvasContainer.getChildren().add(graphCanvas);

        // Bind canvas size dynamically to container
        graphCanvas.widthProperty().bind(canvasContainer.widthProperty());
        graphCanvas.heightProperty().bind(canvasContainer.heightProperty());
        root.setCenter(canvasContainer);

        // Right Sidebar: SplitPane with Document/Chunk viewer (top) and Chat (bottom)
        SplitPane sidebar = createRightSidebar();
        sidebar.setPrefWidth(420);
        root.setRight(sidebar);

        // Setup Selection Listener on Canvas
        graphCanvas.setSelectionListener(this::displayChunkInInspector);

        // Start Physics and Animation Loop
        graphCanvas.start();

        // Setup Drag-and-Drop file ingestion
        root.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        root.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    ingestFile(file);
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        // Load initial data from database
        loadGraphFromDatabase();

        Scene scene = new Scene(root, 1280, 800);
        applyDarkTheme(scene);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        if (graphCanvas != null) {
            graphCanvas.stop();
        }
        if (ingestor != null) {
            ingestor.close();
        }
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
        super.stop();
    }

    private ToolBar createTopToolbar(Stage stage) {
        ingestButton = new Button("📂 Ingest Document");
        ingestButton.setStyle("-fx-background-color: #89B4FA; -fx-text-fill: #11111B; -fx-font-weight: bold;");
        ingestButton.setOnAction(e -> handleIngestDocument(stage));

        Button resetViewButton = new Button("⟲ Reset View");
        resetViewButton.setStyle("-fx-background-color: #313244; -fx-text-fill: #CDD6F4;");
        resetViewButton.setOnAction(e -> graphCanvas.resetView());

        Label thresholdLabel = new Label("Similarity Edge Threshold:");
        thresholdLabel.setStyle("-fx-text-fill: #A6ADC8;");

        thresholdSlider = new Slider(0.40, 0.95, 0.70);
        thresholdSlider.setShowTickMarks(true);
        thresholdSlider.setShowTickLabels(true);
        thresholdSlider.setPrefWidth(120);

        Button rebuildEdgesButton = new Button("⚡ Rebuild Edges");
        rebuildEdgesButton.setStyle("-fx-background-color: #313244; -fx-text-fill: #CDD6F4;");
        rebuildEdgesButton.setOnAction(e -> rebuildGraphEdges());

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #A6E3A1; -fx-font-weight: bold;");

        ToolBar toolBar = new ToolBar(
                ingestButton,
                new Separator(),
                resetViewButton,
                new Separator(),
                thresholdLabel,
                thresholdSlider,
                rebuildEdgesButton,
                new Separator(),
                statusLabel
        );
        toolBar.setStyle("-fx-background-color: #181825; -fx-border-color: #313244; -fx-border-width: 0 0 1 0;");
        return toolBar;
    }

    private SplitPane createRightSidebar() {
        SplitPane sidebar = new SplitPane();
        sidebar.setOrientation(Orientation.VERTICAL);
        sidebar.setDividerPositions(0.45);
        sidebar.setStyle("-fx-background-color: #181825;");

        // Top Pane: Ingested Documents & Selected Chunk Viewer
        VBox topPane = new VBox(8);
        topPane.setPadding(new Insets(10));
        topPane.setStyle("-fx-background-color: #181825;");

        Label docsHeader = new Label("📚 Ingested Documents");
        docsHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #89B4FA;");

        documentsObservable = FXCollections.observableArrayList();
        ListView<String> docsListView = new ListView<>(documentsObservable);
        docsListView.setPrefHeight(90);
        docsListView.setStyle("-fx-background-color: #1E1E2E; -fx-control-inner-background: #1E1E2E; -fx-text-fill: #CDD6F4;");

        Label inspectorHeader = new Label("🔍 Selected Chunk Source");
        inspectorHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #FAB387;");

        HBox metaRow = new HBox(12);
        inspectorDocLabel = new Label("Doc: None");
        inspectorPageLabel = new Label("Page: -");
        inspectorChunkIdLabel = new Label("ID: -");
        inspectorDocLabel.setStyle("-fx-text-fill: #A6ADC8; -fx-font-size: 11px;");
        inspectorPageLabel.setStyle("-fx-text-fill: #A6ADC8; -fx-font-size: 11px;");
        inspectorChunkIdLabel.setStyle("-fx-text-fill: #A6ADC8; -fx-font-size: 11px;");
        metaRow.getChildren().addAll(inspectorDocLabel, inspectorPageLabel, inspectorChunkIdLabel);

        inspectorContentArea = new TextArea("Click on any graph node to inspect its content.");
        inspectorContentArea.setWrapText(true);
        inspectorContentArea.setEditable(false);
        inspectorContentArea.setStyle("-fx-control-inner-background: #1E1E2E; -fx-text-fill: #CDD6F4; -fx-font-family: monospace;");
        VBox.setVgrow(inspectorContentArea, Priority.ALWAYS);

        topPane.getChildren().addAll(docsHeader, docsListView, inspectorHeader, metaRow, inspectorContentArea);

        // Bottom Pane: Grounded RAG Chat
        VBox bottomPane = new VBox(8);
        bottomPane.setPadding(new Insets(10));
        bottomPane.setStyle("-fx-background-color: #181825;");

        Label chatHeader = new Label("💬 Grounded RAG Assistant");
        chatHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #A6E3A1;");

        chatObservable = FXCollections.observableArrayList();
        chatListView = new ListView<>(chatObservable);
        chatListView.setStyle("-fx-background-color: #1E1E2E; -fx-control-inner-background: #1E1E2E; -fx-text-fill: #CDD6F4;");
        VBox.setVgrow(chatListView, Priority.ALWAYS);

        HBox inputRow = new HBox(8);
        chatInputField = new TextField();
        chatInputField.setPromptText("Ask a question across ingested documents...");
        chatInputField.setStyle("-fx-background-color: #313244; -fx-text-fill: #CDD6F4;");
        HBox.setHgrow(chatInputField, Priority.ALWAYS);
        chatInputField.setOnAction(e -> handleSendMessage());

        sendButton = new Button("Send");
        sendButton.setStyle("-fx-background-color: #A6E3A1; -fx-text-fill: #11111B; -fx-font-weight: bold;");
        sendButton.setOnAction(e -> handleSendMessage());

        inputRow.getChildren().addAll(chatInputField, sendButton);
        bottomPane.getChildren().addAll(chatHeader, chatListView, inputRow);

        sidebar.getItems().addAll(topPane, bottomPane);
        return sidebar;
    }

    private void handleIngestDocument(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Document to Ingest");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Supported Documents (*.pdf, *.txt, *.md)", "*.pdf", "*.txt", "*.md"),
                new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"),
                new FileChooser.ExtensionFilter("Text / Markdown Files (*.txt, *.md)", "*.txt", "*.md")
        );

        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            ingestFile(file);
        }
    }

    /**
     * Ingests a document asynchronously, persists chunks/embeddings to SQLite, and updates UI.
     * Accessible to both FileChooser and Drag-and-Drop handlers.
     */
    public CompletableFuture<Void> ingestFile(File file) {
        if (file == null || !file.exists()) {
            return CompletableFuture.completedFuture(null);
        }

        if (ingestButton != null) {
            ingestButton.setDisable(true);
        }
        statusLabel.setText("Ingesting: " + file.getName() + "...");

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate parser exists before touching database
                ingestor.getParserFor(file.getName());

                DocumentRecord doc = new DocumentRecord(file.getName(), file.getAbsolutePath(), file.length());
                int docId = documentDAO.save(doc);

                try {
                    List<TextChunk> chunks = ingestor.ingestAsync(docId, file).get();
                    chunkDAO.saveAll(chunks);

                    // Fetch saved chunks with assigned IDs
                    List<TextChunk> savedChunks = chunkDAO.findByDocumentId(docId);
                    for (TextChunk chunk : savedChunks) {
                        embeddingService.embedChunk(chunk);
                    }
                    embeddingService.persistToDatabase(embeddingDAO);

                    return savedChunks;
                } catch (Exception ex) {
                    // Rollback document record on parsing/embedding failure to prevent orphan records
                    try {
                        documentDAO.deleteById(docId);
                    } catch (Exception ignored) {}
                    throw ex;
                }
            } catch (Exception ex) {
                throw new RuntimeException("Ingestion error: " + ex.getMessage(), ex);
            }
        }, backgroundExecutor).thenAccept(savedChunks -> Platform.runLater(() -> {
            if (ingestButton != null) {
                ingestButton.setDisable(false);
            }
            statusLabel.setText("Ingested: " + file.getName());
            loadGraphFromDatabase();
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                if (ingestButton != null) {
                    ingestButton.setDisable(false);
                }
                statusLabel.setText("Error ingesting file");
                Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
                alert.showAndWait();
            });
            return null;
        });
    }

    private void handleSendMessage() {
        String query = chatInputField.getText();
        if (query == null || query.isBlank()) return;

        chatInputField.clear();
        chatObservable.add("👤 You: " + query.trim());
        chatListView.scrollTo(chatObservable.size() - 1);

        sendButton.setDisable(true);
        statusLabel.setText("Thinking...");

        CompletableFuture.supplyAsync(() -> {
            try {
                return chatService.askQuestion(query, 3);
            } catch (Exception ex) {
                throw new RuntimeException("Chat error: " + ex.getMessage(), ex);
            }
        }, backgroundExecutor).thenAccept(response -> Platform.runLater(() -> {
            chatObservable.add("🤖 Assistant:\n" + response.getContent());
            chatListView.scrollTo(chatObservable.size() - 1);
            sendButton.setDisable(false);
            statusLabel.setText("Ready");
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                chatObservable.add("⚠️ Error: " + ex.getMessage());
                sendButton.setDisable(false);
                statusLabel.setText("Ready");
            });
            return null;
        });
    }

    private void loadGraphFromDatabase() {
        try {
            List<DocumentRecord> docs = documentDAO.findAll();
            documentsObservable.clear();
            for (DocumentRecord doc : docs) {
                documentsObservable.add("• " + doc.getFilename() + " (" + doc.getFileSize() + " bytes)");
            }

            graphCanvas.getPhysicsEngine().clear();
            Map<Integer, DocumentRecord> docMap = new HashMap<>();
            List<TextChunk> allChunks = new ArrayList<>();

            for (DocumentRecord doc : docs) {
                docMap.put(doc.getId(), doc);
                allChunks.addAll(chunkDAO.findByDocumentId(doc.getId()));
            }

            double w = graphCanvas.getWidth() > 0 ? graphCanvas.getWidth() : 800.0;
            double h = graphCanvas.getHeight() > 0 ? graphCanvas.getHeight() : 650.0;
            double centerX = w / 2.0;
            double centerY = h / 2.0;

            for (int i = 0; i < allChunks.size(); i++) {
                TextChunk chunk = allChunks.get(i);
                DocumentRecord doc = docMap.get(chunk.getDocumentId());
                String docName = doc != null ? doc.getFilename() : "Unknown";

                // Arrange initial positions in a loose circular distribution around center
                double angle = (2 * Math.PI * i) / Math.max(1, allChunks.size());
                double radius = 100.0 + (i % 3) * 40.0;
                double px = centerX + radius * Math.cos(angle);
                double py = centerY + radius * Math.sin(angle);

                GraphNode node = new GraphNode(
                        chunk.getId(),
                        chunk.getDocumentId(),
                        docName,
                        chunk.getPageNumber(),
                        chunk.getContent(),
                        px,
                        py
                );
                graphCanvas.getPhysicsEngine().addNode(node);
            }

            // Build similarity edges
            double threshold = thresholdSlider != null ? thresholdSlider.getValue() : 0.70;
            List<GraphEdge> edges = searchService.buildSimilarityEdges(allChunks, embeddingService, threshold);
            for (GraphEdge edge : edges) {
                graphCanvas.getPhysicsEngine().addEdge(edge);
            }

            // Load chat history
            List<ChatMessage> history = chatDAO.findAll();
            chatObservable.clear();
            for (ChatMessage msg : history) {
                if ("user".equalsIgnoreCase(msg.getSender())) {
                    chatObservable.add("👤 You: " + msg.getContent());
                } else {
                    chatObservable.add("🤖 Assistant:\n" + msg.getContent());
                }
            }
        } catch (Exception ex) {
            statusLabel.setText("Database sync error: " + ex.getMessage());
        }
    }

    private void rebuildGraphEdges() {
        double threshold = thresholdSlider.getValue();
        statusLabel.setText(String.format("Rebuilding edges (threshold: %.2f)...", threshold));

        CompletableFuture.supplyAsync(() -> {
            try {
                List<DocumentRecord> docs = documentDAO.findAll();
                List<TextChunk> allChunks = new ArrayList<>();
                for (DocumentRecord doc : docs) {
                    allChunks.addAll(chunkDAO.findByDocumentId(doc.getId()));
                }

                List<GraphEdge> edges = searchService.buildSimilarityEdges(allChunks, embeddingService, threshold);
                return Map.entry(allChunks.size(), edges);
            } catch (Exception ex) {
                throw new RuntimeException("Error computing edges: " + ex.getMessage(), ex);
            }
        }, backgroundExecutor).thenAccept(entry -> Platform.runLater(() -> {
            graphCanvas.getPhysicsEngine().clearEdges();
            for (GraphEdge edge : entry.getValue()) {
                graphCanvas.getPhysicsEngine().addEdge(edge);
            }
            statusLabel.setText(String.format("Graph rebuilt: %d nodes, %d edges", entry.getKey(), entry.getValue().size()));
        })).exceptionally(ex -> {
            Platform.runLater(() -> statusLabel.setText("Error rebuilding edges: " + ex.getMessage()));
            return null;
        });
    }

    private void displayChunkInInspector(GraphNode node) {
        if (node == null) return;
        inspectorDocLabel.setText("Doc: " + node.getDocumentName());
        inspectorPageLabel.setText("Page: " + node.getPageNumber());
        inspectorChunkIdLabel.setText("Chunk ID: " + node.getChunkId());
        inspectorContentArea.setText(node.getContent());
    }

    private void applyDarkTheme(Scene scene) {
        scene.getRoot().setStyle("-fx-base: #181825; -fx-background: #11111B; -fx-control-inner-background: #1E1E2E; -fx-text-fill: #CDD6F4;");
    }
}

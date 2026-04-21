package server;

import common.Message;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class ChatServer extends Application {

    // Shared state -protected by locks
    private final Map<String, ClientHandler> clients   = new HashMap<>();
    private final Map<String, ChatRoom>      rooms     = new HashMap<>();
    private final ReentrantLock              clientsLock = new ReentrantLock();
    private final ReentrantLock              roomsLock   = new ReentrantLock();

    // Network
    private ServerSocket    serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running = false;

    // JavaFX components
    private TextArea              logArea;
    private ObservableList<String> clientsObs;
    private ObservableList<String> roomsObs;
    private Button                 startStopBtn;
    private TextField              portField;
    private Label                  statusLabel;

    // JavaFX lifecycle
    @Override
    public void start(Stage stage) {
        stage.setTitle("Distributed Chat — Server");

        // Top bar
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(10, 14, 10, 14));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #1E3A5F;");

        Label portLbl = new Label("Port:");
        portLbl.setStyle("-fx-text-fill: white;");
        portField = new TextField("12345");
        portField.setPrefWidth(72);

        startStopBtn = new Button("▶  Start Server");
        startStopBtn.setStyle(
                "-fx-background-color: #27AE60; -fx-text-fill: white; -fx-font-weight: bold;");
        startStopBtn.setOnAction(e -> toggleServer());

        statusLabel = new Label("⬤  Stopped");
        statusLabel.setStyle("-fx-text-fill: #E74C3C; -fx-font-weight: bold;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topBar.getChildren().addAll(portLbl, portField, startStopBtn, spacer, statusLabel);

        //Left panel: clients & rooms lists
        VBox leftPanel = new VBox(8);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setPrefWidth(210);
        leftPanel.setStyle("-fx-background-color: #F0F4F8;");

        Label clientsLbl = styledLabel("Connected Clients");
        clientsObs = FXCollections.observableArrayList();
        ListView<String> clientsView = new ListView<>(clientsObs);
        clientsView.setPrefHeight(200);
        VBox.setVgrow(clientsView, Priority.ALWAYS);

        Label roomsLbl = styledLabel("Active Rooms");
        roomsObs = FXCollections.observableArrayList();
        ListView<String> roomsView = new ListView<>(roomsObs);
        roomsView.setPrefHeight(200);
        VBox.setVgrow(roomsView, Priority.ALWAYS);

        leftPanel.getChildren().addAll(clientsLbl, clientsView, roomsLbl, roomsView);

        // Center: server log
        VBox centerPanel = new VBox(6);
        centerPanel.setPadding(new Insets(10));

        Label logLbl = styledLabel("Server Log");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 12;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        Button clearBtn = new Button("Clear log");
        clearBtn.setOnAction(e -> logArea.clear());

        centerPanel.getChildren().addAll(logLbl, logArea, clearBtn);

        // Root layout
        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setLeft(leftPanel);
        root.setCenter(centerPanel);

        Scene scene = new Scene(root, 780, 540);
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(e -> {
            stopServer();
            Platform.exit();
        });
    }

    // Server start / stop
    private void toggleServer() {
        if (running) stopServer();
        else         startServer();
    }

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            log("Invalid port number.");
            return;
        }

        threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        int finalPort = port;
        Thread acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(finalPort);
                running = true;

                Platform.runLater(() -> {
                    startStopBtn.setText("■  Stop Server");
                    startStopBtn.setStyle(
                            "-fx-background-color: #C0392B; -fx-text-fill: white; -fx-font-weight: bold;");
                    statusLabel.setText("⬤  Running on port " + finalPort);
                    statusLabel.setStyle("-fx-text-fill: #27AE60; -fx-font-weight: bold;");
                    portField.setDisable(true);
                });

                log("Server started on port " + finalPort);

                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        client.setTcpNoDelay(true);
                        ClientHandler handler = new ClientHandler(client, this);
                        threadPool.execute(handler);
                    } catch (SocketException e) {
                        if (running) log("Accept error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                log("Server error: " + e.getMessage());
            }
        }, "Accept-Thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void stopServer() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
        } catch (IOException ignored) {}

        if (threadPool != null) threadPool.shutdownNow();

        Platform.runLater(() -> {
            startStopBtn.setText("▶  Start Server");
            startStopBtn.setStyle(
                    "-fx-background-color: #27AE60; -fx-text-fill: white; -fx-font-weight: bold;");
            statusLabel.setText("⬤  Stopped");
            statusLabel.setStyle("-fx-text-fill: #E74C3C; -fx-font-weight: bold;");
            portField.setDisable(false);
            clientsObs.clear();
            roomsObs.clear();
        });
        log("Server stopped.");
    }

    // Client registry (thread-safe)
    // Returns true if the username was free and has been registered.
    public boolean registerClient(String username, ClientHandler handler) {
        clientsLock.lock();
        try {
            if (clients.containsKey(username)) return false;
            clients.put(username, handler);
            Platform.runLater(() -> clientsObs.add(username));
            return true;
        } finally {
            clientsLock.unlock();
        }
    }

    public void removeClient(String username) {
        if (username == null) return;
        clientsLock.lock();
        try {
            clients.remove(username);
            Platform.runLater(() -> clientsObs.remove(username));
        } finally {
            clientsLock.unlock();
        }
    }

    // Room registry (thread-safe)
    // Creates a new room and returns it, or null if the name is taken.
    public ChatRoom createRoom(String name) {
        roomsLock.lock();
        try {
            if (rooms.containsKey(name)) return null;
            ChatRoom room = new ChatRoom(name);
            rooms.put(name, room);
            return room;
        } finally {
            roomsLock.unlock();
        }
    }

    public ChatRoom getRoom(String name) {
        roomsLock.lock();
        try {
            return rooms.get(name);
        } finally {
            roomsLock.unlock();
        }
    }

    public List<String> getRoomNames() {
        roomsLock.lock();
        try {
            return new ArrayList<>(rooms.keySet());
        } finally {
            roomsLock.unlock();
        }
    }

    // Rebuilds the rooms list view (member counts). Call after any join/leave.
    public void refreshRoomsDisplay() {
        roomsLock.lock();
        try {
            List<String> display = new ArrayList<>();
            for (Map.Entry<String, ChatRoom> e : rooms.entrySet()) {
                display.add(e.getKey() + "  (" + e.getValue().getMemberCount() + ")");
            }
            Platform.runLater(() -> roomsObs.setAll(display));
        } finally {
            roomsLock.unlock();
        }
    }

    // Logging
    public void log(String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String line = "[" + time + "] " + message;
        Platform.runLater(() -> {
            logArea.appendText(line + "\n");
            // Auto-scroll
            logArea.setScrollTop(Double.MAX_VALUE);
        });
        System.out.println(line);
    }

    // Utility
    private Label styledLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        return lbl;
    }

    // Entry point
    public static void main(String[] args) {
        launch(args);
    }
}

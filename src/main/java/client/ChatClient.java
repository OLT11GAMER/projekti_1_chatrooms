package client;

import common.Message;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.io.EOFException;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

public class ChatClient extends Application {

    // Network state
    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private volatile boolean   connected = false;
    private String             username;
    private String             currentRoom;

    // JavaFX stage reference
    private Stage primaryStage;

    // Login scene controls
    private TextField usernameField;
    private TextField hostField;
    private TextField portField;
    private Button    connectButton;
    private Label     loginStatus;

    //Chat scene controls
    private TextArea         chatArea;
    private TextField        messageField;
    private ListView<String> roomsListView;
    private ListView<String> usersListView;
    private Label            currentRoomLabel;
    private Label            userInfoLabel;
    private TextField        newRoomField;

    //Application lifecycle

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("Distributed Chat");
        showLoginScene();
        stage.show();

        stage.setOnCloseRequest(e -> {
            disconnect();
            Platform.exit();
        });
    }

    // Login Part
    private void showLoginScene() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(40));
        grid.setHgap(10);
        grid.setVgap(14);
        grid.setAlignment(Pos.CENTER);
        grid.setStyle("-fx-background-color: #F5F7FA;");

        // Title
        Label title = new Label("Distributed Chat System");
        title.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #1E3A5F;");
        Label subtitle = new Label("Connect to a server to start chatting");
        subtitle.setStyle("-fx-font-size: 12; -fx-text-fill: #7F8C8D;");
        VBox titleBox = new VBox(4, title, subtitle);
        titleBox.setAlignment(Pos.CENTER);
        grid.add(titleBox, 0, 0, 2, 1);

        // Fields
        grid.add(fieldLabel("Username"), 0, 1);
        usernameField = styledField("Choose a username", 220);
        grid.add(usernameField, 1, 1);

        grid.add(fieldLabel("Server host"), 0, 2);
        hostField = styledField("localhost", 220);
        grid.add(hostField, 1, 2);

        grid.add(fieldLabel("Port"), 0, 3);
        portField = styledField("12345", 220);
        grid.add(portField, 1, 3);

        connectButton = new Button("Connect");
        connectButton.setPrefWidth(240);
        connectButton.setStyle(
                "-fx-background-color: #2980B9; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-font-size: 13; -fx-padding: 8 0;");
        connectButton.setOnAction(e -> attemptConnect());
        grid.add(connectButton, 0, 4, 2, 1);

        loginStatus = new Label("");
        loginStatus.setStyle("-fx-text-fill: #E74C3C;");
        loginStatus.setWrapText(true);
        grid.add(loginStatus, 0, 5, 2, 1);

        // Enter key on any field triggers connect
        usernameField.setOnAction(e -> attemptConnect());
        portField.setOnAction(e -> attemptConnect());

        Scene scene = new Scene(grid, 400, 330);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
    }

    // Connection Part
    private void attemptConnect() {
        String user = usernameField.getText().trim();
        String host = hostField.getText().trim();

        if (user.isEmpty()) { setLoginError("Please enter a username."); return; }
        if (host.isEmpty()) { setLoginError("Please enter a server host."); return; }

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            setLoginError("Invalid port number.");
            return;
        }

        connectButton.setDisable(true);
        loginStatus.setStyle("-fx-text-fill: #2980B9;");
        loginStatus.setText("Connecting…");

        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                socket.setTcpNoDelay(true);
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in  = new ObjectInputStream(socket.getInputStream());

                // ✅ Write LOGIN directly — don't use sendMessage() here because
                //    sendMessage() checks 'connected', which is still false at this point.
                out.writeObject(new Message(Message.Type.LOGIN, user, user, null));
                out.flush();
                out.reset();

                Message response = (Message) in.readObject();

                if (response.getType() == Message.Type.LOGIN_SUCCESS) {
                    username  = user;
                    connected = true;   // ← only set true AFTER confirmed by server
                    Platform.runLater(() -> {
                        showChatScene();
                        appendToChat("✓ " + response.getContent());
                    });
                    startReceiving();
                } else {
                    Platform.runLater(() -> {
                        setLoginError(response.getContent());
                        connectButton.setDisable(false);
                    });
                    socket.close();
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setLoginError("Connection failed: " + e.getMessage());
                    connectButton.setDisable(false);
                });
            }
        }, "Connect-Thread").start();
    }
    // Chat Part
    private void showChatScene() {
        primaryStage.setTitle("Chat — " + username);
        primaryStage.setResizable(true);

        BorderPane root = new BorderPane();

        // Top bar
        HBox topBar = new HBox(14);
        topBar.setPadding(new Insets(10, 14, 10, 14));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #1E3A5F;");

        userInfoLabel = new Label("👤 " + username);
        userInfoLabel.setStyle("-fx-text-fill: #ECF0F1; -fx-font-weight: bold;");

        currentRoomLabel = new Label("No room joined");
        currentRoomLabel.setStyle("-fx-text-fill: #BDC3C7;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button disconnectBtn = new Button("Disconnect");
        disconnectBtn.setStyle("-fx-background-color: #C0392B; -fx-text-fill: white;");
        disconnectBtn.setOnAction(e -> {
            disconnect();
            showLoginScene();
        });

        topBar.getChildren().addAll(userInfoLabel, currentRoomLabel, spacer, disconnectBtn);
        root.setTop(topBar);

        // ── Left panel: rooms
        VBox leftPanel = new VBox(8);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setPrefWidth(210);
        leftPanel.setStyle("-fx-background-color: #F0F4F8;");

        Label roomsLbl = boldLabel("Available Rooms");
        roomsListView  = new ListView<>();
        roomsListView.setPrefHeight(180);
        VBox.setVgrow(roomsListView, Priority.ALWAYS);

        // Double-click a room to join it
        roomsListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) joinSelectedRoom();
        });

        Label createLbl = boldLabel("Create Room");
        newRoomField = new TextField();
        newRoomField.setPromptText("Room name…");
        newRoomField.setOnAction(e -> createRoom());
        Button createBtn = actionButton("Create", "#27AE60");
        createBtn.setOnAction(e -> createRoom());

        Button joinBtn  = actionButton("Join Selected",  "#2980B9");
        Button leaveBtn = actionButton("Leave Room",     "#C0392B");
        joinBtn.setOnAction(e  -> joinSelectedRoom());
        leaveBtn.setOnAction(e -> sendMessage(new Message(
                Message.Type.LEAVE_ROOM, username, "", null)));

        Button refreshBtn = new Button("↺  Refresh list");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.setOnAction(e -> sendMessage(new Message(
                Message.Type.LIST_ROOMS, username, "", null)));

        Label usersLbl = boldLabel("Users in Room");
        usersListView  = new ListView<>();
        usersListView.setPrefHeight(120);

        leftPanel.getChildren().addAll(
                roomsLbl, roomsListView,
                createLbl, newRoomField, createBtn,
                joinBtn, leaveBtn, refreshBtn,
                usersLbl, usersListView);
        root.setLeft(leftPanel);

        // Center: chat area
        VBox centerPanel = new VBox(6);
        centerPanel.setPadding(new Insets(10));

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle("-fx-font-size: 13; -fx-font-family: 'Segoe UI', Arial, sans-serif;");
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        HBox inputRow = new HBox(8);
        inputRow.setAlignment(Pos.CENTER);
        messageField = new TextField();
        messageField.setPromptText("Type a message and press Enter…");
        messageField.setOnAction(e -> sendChat());
        HBox.setHgrow(messageField, Priority.ALWAYS);

        Button sendBtn = actionButton("Send ➤", "#2980B9");
        sendBtn.setOnAction(e -> sendChat());

        inputRow.getChildren().addAll(messageField, sendBtn);
        centerPanel.getChildren().addAll(chatArea, inputRow);
        root.setCenter(centerPanel);

        Scene scene = new Scene(root, 830, 580);
        primaryStage.setScene(scene);
    }

    // RECEIVE THREAD (push-based, no polling)
    private void startReceiving() {
        Thread receiveThread = new Thread(() -> {
            while (connected) {
                try {
                    Message msg = (Message) in.readObject();
                    // Hand off to the JavaFX thread — never mutate UI from here.
                    Platform.runLater(() -> handleIncoming(msg));
                } catch (EOFException | SocketException e) {
                    if (connected) {
                        connected = false;
                        Platform.runLater(() ->
                                appendToChat("⚠  Connection to server lost."));
                    }
                    break;
                } catch (IOException | ClassNotFoundException e) {
                    if (connected) {
                        Platform.runLater(() ->
                                appendToChat("⚠  Receive error: " + e.getMessage()));
                    }
                    break;
                }
            }
        }, "Receive-Thread");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

  // INCOMING MESSAGE HANDLER (always called on JavaFX thread)
    private void handleIncoming(Message msg) {
        switch (msg.getType()) {

            case CHAT_BROADCAST -> appendToChat(
                    "[" + msg.getTimestamp() + "]  " + msg.getSender() + ": " + msg.getContent());

            case SERVER_NOTICE  -> appendToChat(
                    "  ── " + msg.getContent() + " ──");

            case ROOM_LIST      -> updateRoomsList(msg.getDataList());

            case USER_LIST      -> updateUsersList(msg.getDataList());

            case ROOM_JOINED    -> {
                currentRoom = msg.getRoomName();
                currentRoomLabel.setText("# " + currentRoom);
                appendToChat("\n━━  Joined room: " + currentRoom + "  ━━\n");
            }

            case ROOM_LEFT      -> {
                appendToChat("━━  You left the room.  ━━\n");
                currentRoom = null;
                currentRoomLabel.setText("No room joined");
                usersListView.getItems().clear();
            }

            case ERROR          -> appendToChat("⚠  " + msg.getContent());

            default             -> { /* ignore unexpected types */ }
        }
    }

    // USER ACTIONS
    private void createRoom() {
        String name = newRoomField.getText().trim();
        if (name.isEmpty()) return;
        sendMessage(new Message(Message.Type.CREATE_ROOM, username, "", name));
        newRoomField.clear();
    }

    private void joinSelectedRoom() {
        String selected = roomsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            appendToChat("⚠  Select a room from the list first.");
            return;
        }
        sendMessage(new Message(Message.Type.JOIN_ROOM, username, "", selected));
    }

    private void sendChat() {
        if (messageField == null) return;
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        if (currentRoom == null) {
            appendToChat("⚠  Join a room before sending messages.");
            return;
        }
        sendMessage(new Message(Message.Type.CHAT, username, text, currentRoom));
        messageField.clear();
    }

 
    // NETWORK HELPERS
    /** Thread-safe socket write. */
    public synchronized void sendMessage(Message msg) {
        try {
            if (out != null && connected) {
                out.writeObject(msg);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            appendToChat("⚠  Send failed: " + e.getMessage());
        }
    }

    private void disconnect() {
        if (!connected) return;
        connected = false;
        try {
            if (out != null)
                sendMessage(new Message(Message.Type.DISCONNECT, username, "", null));
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException ignored) {}
        currentRoom = null;
    }

    // UI HELPERS
    private void appendToChat(String text) {
        if (chatArea != null) {
            chatArea.appendText(text + "\n");
            chatArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    private void updateRoomsList(List<String> rooms) {
        if (roomsListView == null || rooms == null) return;
        roomsListView.getItems().setAll(rooms);
    }

    private void updateUsersList(List<String> users) {
        if (usersListView == null || users == null) return;
        usersListView.getItems().setAll(users);
    }

    private void setLoginError(String msg) {
        loginStatus.setStyle("-fx-text-fill: #E74C3C;");
        loginStatus.setText(msg);
    }

    private Label boldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");
        return l;
    }

    private Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-text-fill: #2C3E50;");
        return l;
    }

    private TextField styledField(String prompt, double width) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.setPrefWidth(width);
        return f;
    }

    private Button actionButton(String text, String color) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold;");
        return b;
    }

    //Entry point
    public static void main(String[] args) {
        launch(args);
    }
}

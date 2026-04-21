package server;

import common.Message;

import java.io.*;
import java.io.EOFException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable {

    private final Socket      socket;
    private final ChatServer  server;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private String             username;
    private ChatRoom           currentRoom;
    private volatile boolean   running;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket  = socket;
        this.server  = server;
        this.running = true;
    }

    // Thread entry point

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());
            if (!handleLogin()) return;

            while (running) {
                try {
                    Message msg = (Message) in.readObject();
                    handleMessage(msg);
                } catch (EOFException | SocketException e) {
                    break; // client closed connection
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // Connection lost — fall through to finally
        } finally {
            disconnect();
        }
    }

    //Login Part

    private boolean handleLogin() throws IOException, ClassNotFoundException {
        Message loginMsg = (Message) in.readObject();
        if (loginMsg.getType() != Message.Type.LOGIN) return false;

        String requested = loginMsg.getContent().trim();

        if (requested.isEmpty()) {
            sendMessage(new Message(Message.Type.LOGIN_FAIL, "Server",
                    "Username cannot be empty.", null));
            return false;
        }

        if (server.registerClient(requested, this)) {
            this.username = requested;
            sendMessage(new Message(Message.Type.LOGIN_SUCCESS, "Server",
                    "Welcome, " + username + "!", null));
            server.log("Connected: " + username + " @ " + socket.getInetAddress());
            sendRoomList(); // send available rooms immediately after login
            return true;
        } else {
            sendMessage(new Message(Message.Type.LOGIN_FAIL, "Server",
                    "Username \"" + requested + "\" is already taken. Choose another.", null));
            return false;
        }
    }

    // Message routing

    private void handleMessage(Message msg) {
        switch (msg.getType()) {
            case CREATE_ROOM  -> handleCreateRoom(msg.getRoomName());
            case JOIN_ROOM    -> handleJoinRoom(msg.getRoomName());
            case LEAVE_ROOM   -> handleLeaveRoom();
            case CHAT         -> handleChat(msg);
            case LIST_ROOMS   -> sendRoomList();
            case DISCONNECT   -> running = false;
            default           -> { /* ignore unknown types */ }
        }
    }

    // Room operations
    private void handleCreateRoom(String roomName) {
        if (roomName == null || roomName.isBlank()) {
            sendMessage(new Message(Message.Type.ERROR, "Server",
                    "Room name cannot be empty.", null));
            return;
        }

        ChatRoom room = server.createRoom(roomName.trim());
        if (room == null) {
            sendMessage(new Message(Message.Type.ERROR, "Server",
                    "Room \"" + roomName + "\" already exists.", null));
            return;
        }

        leaveCurrentRoom();          // leave previous room if any
        currentRoom = room;
        room.addMember(this);

        sendMessage(new Message(Message.Type.ROOM_JOINED, "Server",
                "Room \"" + roomName + "\" created.", roomName));

        room.enqueueMessage(new Message(Message.Type.SERVER_NOTICE, "Server",
                username + " created and joined the room.", roomName));

        server.log("Room created: \"" + roomName + "\" by " + username);
        server.refreshRoomsDisplay();
        sendRoomList();
    }

    private void handleJoinRoom(String roomName) {
        if (roomName == null || roomName.isBlank()) {
            sendMessage(new Message(Message.Type.ERROR, "Server",
                    "Room name cannot be empty.", null));
            return;
        }

        ChatRoom room = server.getRoom(roomName.trim());
        if (room == null) {
            sendMessage(new Message(Message.Type.ERROR, "Server",
                    "Room \"" + roomName + "\" does not exist.", null));
            return;
        }

        if (currentRoom != null && currentRoom.getName().equals(roomName)) {
            sendMessage(new Message(Message.Type.ERROR, "Server",
                    "You are already in this room.", null));
            return;
        }

        leaveCurrentRoom();
        currentRoom = room;
        room.addMember(this);

        sendMessage(new Message(Message.Type.ROOM_JOINED, "Server",
                "Joined room \"" + roomName + "\".", roomName));

        room.enqueueMessage(new Message(Message.Type.SERVER_NOTICE, "Server",
                username + " joined the room.", roomName));

        server.log(username + " joined room: \"" + roomName + "\"");
        server.refreshRoomsDisplay();

        // Send member list so the UI can populate the users panel
        List<String> members = room.getMemberNames();
        Message userListMsg = new Message(Message.Type.USER_LIST, "Server",
                "", roomName);
        userListMsg.setDataList(members);
        sendMessage(userListMsg);
    }

    private void handleLeaveRoom() {
        if (currentRoom == null) {
            sendMessage(new Message(Message.Type.ERROR, "Server",
                    "You are not in any room.", null));
            return;
        }
        leaveCurrentRoom();
        sendMessage(new Message(Message.Type.ROOM_LEFT, "Server",
                "You left the room.", null));
        server.refreshRoomsDisplay();
        sendRoomList();
    }

    /** Internal helper — leaves the current room without sending ROOM_LEFT. */
    private void leaveCurrentRoom() {
        if (currentRoom == null) return;
        currentRoom.enqueueMessage(new Message(Message.Type.SERVER_NOTICE, "Server",
                username + " left the room.", currentRoom.getName()));
        currentRoom.removeMember(this);
        server.log(username + " left room: \"" + currentRoom.getName() + "\"");
        currentRoom = null;
    }

    // Chat

    private void handleChat(Message msg) {
        if (currentRoom == null) {
            sendMessage(new Message(Message.Type.ERROR, "Server",
                    "Join a room before sending messages.", null));
            return;
        }
        if (msg.getContent() == null || msg.getContent().isBlank()) return;

        // Build a broadcast message preserving the original timestamp
        Message broadcast = new Message(Message.Type.CHAT_BROADCAST,
                username, msg.getContent(), currentRoom.getName());
        currentRoom.enqueueMessage(broadcast);
    }

    //Helpers

    private void sendRoomList() {
        List<String> names = server.getRoomNames();
        Message msg = new Message(Message.Type.ROOM_LIST, "Server", "", null);
        msg.setDataList(names);
        sendMessage(msg);
    }

    public synchronized void sendMessage(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // prevent ObjectOutputStream from caching stale object graphs
        } catch (IOException e) {
            running = false;
        }
    }

    //Disconnect

    private void disconnect() {
        running = false;
        leaveCurrentRoom();
        server.removeClient(username);
        server.refreshRoomsDisplay();
        try { socket.close(); } catch (IOException ignored) {}
        if (username != null) server.log("Disconnected: " + username);
    }

    //Accessors

    public String   getUsername()    { return username;    }
    public ChatRoom getCurrentRoom() { return currentRoom; }
}

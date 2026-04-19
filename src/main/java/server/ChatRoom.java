package server;

import common.Message;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a chat room.
 *
 * Key concurrency design
 * ──────────────────────
 * • A LinkedBlockingQueue<Message> buffers every incoming chat message.
 * • A single dedicated "dispatcher" thread (one per room) blocks on queue.take()
 *   and broadcasts each message to all current members.
 * • A ReentrantLock guards the members Set so that concurrent join / leave
 *   operations never corrupt the collection while broadcasting is in progress.
 *
 * This satisfies the project's BlockingQueue + synchronisation requirements.
 */
public class ChatRoom {

    private final String                    name;
    private final Set<ClientHandler>        members;
    private final BlockingQueue<Message>    messageQueue;
    private final ReentrantLock             membersLock;
    private final Thread                    dispatchThread;
    private volatile boolean                running;

    public ChatRoom(String name) {
        this.name         = name;
        this.members      = new HashSet<>();
        this.messageQueue = new LinkedBlockingQueue<>();
        this.membersLock  = new ReentrantLock();
        this.running      = true;

        // Dedicated dispatcher thread: blocks until a message is available,
        // then broadcasts it to all current room members.
        this.dispatchThread = new Thread(this::dispatchMessages,
                "Dispatcher-" + name);
        this.dispatchThread.setDaemon(true);
        this.dispatchThread.start();
    }

    // ── Message dispatching ────────────────────────────────────────────────────

    /** Producer side: any ClientHandler enqueues here (non-blocking). */
    public void enqueueMessage(Message msg) {
        messageQueue.offer(msg);
    }

    /** Consumer side: runs on the room's dedicated dispatcher thread. */
    private void dispatchMessages() {
        while (running) {
            try {
                // Blocks until a message arrives — no busy-waiting / polling.
                Message msg = messageQueue.take();
                broadcast(msg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Send a message to every member currently in this room. */
    private void broadcast(Message msg) {
        membersLock.lock();
        try {
            List<ClientHandler> dead = new ArrayList<>();
            for (ClientHandler client : members) {
                try {
                    client.sendMessage(msg);
                } catch (Exception e) {
                    // Remove clients whose socket is no longer writable.
                    dead.add(client);
                }
            }
            members.removeAll(dead);
        } finally {
            membersLock.unlock();
        }
    }

    // ── Membership management ──────────────────────────────────────────────────

    public boolean addMember(ClientHandler client) {
        membersLock.lock();
        try {
            return members.add(client);
        } finally {
            membersLock.unlock();
        }
    }

    public boolean removeMember(ClientHandler client) {
        membersLock.lock();
        try {
            return members.remove(client);
        } finally {
            membersLock.unlock();
        }
    }

    public List<String> getMemberNames() {
        membersLock.lock();
        try {
            List<String> names = new ArrayList<>();
            for (ClientHandler c : members) {
                names.add(c.getUsername());
            }
            return names;
        } finally {
            membersLock.unlock();
        }
    }

    public int getMemberCount() {
        membersLock.lock();
        try {
            return members.size();
        } finally {
            membersLock.unlock();
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public void shutdown() {
        running = false;
        dispatchThread.interrupt();
    }

    public String getName() { return name; }
}

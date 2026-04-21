# Distributed Chat System
### Java · Sockets · Multithreading · BlockingQueue · JavaFX

---

## Udhëzime për ekzekutim

### Kërkesat paraprake
| Mjeti | Versioni minimal |
|-------|-----------------|
| JDK   | 17              |
| Maven | 3.8+            |

### Hapi 1 – Build
```bash
cd distributed-chat
mvn clean package -q
```

### Hapi 2 – Nisja e Serverit
```bash
java -cp target/distributed-chat-1.0-SNAPSHOT-server.jar server.ChatServer
```
Serveri starton në **port 5000** dhe krijon automatikisht room-in `General`.  
Për port të ndryshëm:
```bash
java -cp target/distributed-chat-1.0-SNAPSHOT-server.jar server.ChatServer 6000
```

### Hapi 3 – Nisja e Klientëve (minimum 3)
```bash
mvn javafx:run
```
Çdo klient hap dritaren e login-it. Fut `localhost`, `5000`, dhe një username unik, pastaj kliko **Connect**.

> **Testimi me 3 klientë:** Hap 3 terminale të ndara dhe ekzekuto `mvn javafx:run` në secilin.

---

## Përshkrim i arkitekturës

### Pamje e përgjithshme

```
┌────────────────────────────────────────────────────────────────┐
│                        CHAT SERVER                             │
│                                                                │
│  ServerSocket ──► ExecutorService (CachedThreadPool)           │
│                        │                                       │
│               ┌────────┴────────┐                              │
│        ClientHandler-1    ClientHandler-N  (1 thread/klient)  │
│               │                                                │
│          RoomManager (ConcurrentHashMap)                       │
│           ├── ChatRoom "General"                               │
│           │     ├── LinkedBlockingQueue<Packet>                │
│           │     ├── DispatcherThread  (take → broadcast)       │
│           │     └── Set<ClientHandler>  (ReentrantLock)        │
│           └── ChatRoom "Random"  (strukturë identike)          │
│                                                                │
│  connectedClients Map  (ReentrantLock)                         │
└────────────────────────────────────────────────────────────────┘
                    ▲  TCP Socket (Packet Serializable)
                    ▼
┌────────────────────────────────────────────────────────────────┐
│                        CHAT CLIENT                             │
│                                                                │
│  JavaFX Application Thread  ── tërë UI-ja                      │
│  ReceiverThread              ── bllokon mbi ObjectInputStream   │
│                                 Platform.runLater() për UI     │
└────────────────────────────────────────────────────────────────┘
```

### Rrjedha e mesazhit (Message Flow)
```
Klienti          ClientHandler        BlockingQueue      DispatcherThread     Anëtarët
   │                   │                    │                   │                │
   │── SEND_MESSAGE ──►│                    │                   │                │
   │                   │── enqueue() ──────►│                   │                │
   │                   │                    │── take() ────────►│                │
   │                   │                    │                   │── broadcast() ─►│
   │◄──────────────────│────────────────────│───── MESSAGE_BROADCAST ────────────│
```

### Klasat kryesore

| Klasa | Paketa | Roli |
|-------|--------|------|
| `ChatServer` | `server` | ServerSocket + ThreadPool + regjistri i klientëve |
| `ClientHandler` | `server` | Menaxhon një klient në thread të veçantë (Runnable) |
| `ChatRoom` | `server` | BlockingQueue + DispatcherThread + lista e anëtarëve |
| `RoomManager` | `server` | Regjistri i të gjitha room-eve (ConcurrentHashMap) |
| `Packet` | `shared` | Mesazhi serializable i protokollit (Builder pattern) |
| `CommandType` | `shared` | Enum me të gjitha komandat e protokollit |
| `ChatClientApp` | `client` | JavaFX GUI + ReceiverThread |

---

## Kërkesat teknike – si janë plotësuar

### 1. Multithreading
- `ChatServer` përdor `ExecutorService` (`CachedThreadPool`) — çdo klient merr thread-in e vet
- `ChatRoom` ka `DispatcherThread` të dedikuar për çdo room
- Klienti ka `ReceiverThread` të veçantë për marrjen e mesazheve pa bllokuar UI-në

### 2. BlockingQueue
- `ChatRoom` përdor `LinkedBlockingQueue<Packet>`
- `ClientHandler.handleSendMessage()` → `enqueueMessage()` → fut në queue
- `DispatcherThread.dispatchLoop()` → `queue.take()` bllokon pa harxhuar CPU, zgjohet vetëm kur vjen mesazh
- Kjo është **push-based system** — nuk ka polling

### 3. Sinkronizimi
| Struktura | Mbrojtja |
|-----------|---------|
| `connectedClients` (Map në ChatServer) | `ReentrantLock` — `clientsLock` |
| `members` (Set në ChatRoom) | `ReentrantLock` — `membersLock` |
| Room map (RoomManager) | `ConcurrentHashMap` — thread-safe pa lock shtesë |
| `ObjectOutputStream` (ClientHandler) | `synchronized` në `sendPacket()` |

**Snapshot pattern** — brenda `broadcast()`, lista e anëtarëve kopjohet nën lock, pastaj lock-u lirohet **para** I/O. Kjo parandalon bllokim të gjatë dhe `ConcurrentModificationException`.

### 4. Programimi në rrjet
- `ServerSocket` në `ChatServer.start()`
- `Socket` për çdo klient të lidhur
- `ObjectOutputStream` / `ObjectInputStream` për serializim të `Packet`-ave

### 5. Protokolli i komunikimit
Çdo mesazh është objekt `Packet implements Serializable` me fushat:
- `CommandType type` — lloji i komandës
- `String sender` — dërguesi
- `String roomName` — room-i
- `String content` — përmbajtja
- `LocalDateTime timestamp` — ora e dërgimit (vendoset automatikisht)
- `List<String> data` — payload për lista (rooms, users)

---

## Problemet e hasura dhe zgjidhjet

### Problemi 1 – Deadlock me ObjectOutputStream
**Problemi:** Të dy anët ndërtuan `ObjectInputStream` para `ObjectOutputStream`, duke shkaktuar bllok të ndërsjellë (secila priste header-in e streamit të tjetrës).  
**Zgjidhja:** Gjithmonë ndërtoje `ObjectOutputStream` së pari dhe thirre `flush()`, pastaj `ObjectInputStream`.

### Problemi 2 – Cache i vjetër i serializimit
**Problemi:** Objekte të modifikuara dhe të dërguara shumë herë shfaqeshin me vlerat e vjetra sepse `ObjectOutputStream` ruan cache të referencave.  
**Zgjidhja:** Thirre `out.reset()` pas çdo `writeObject()`.

### Problemi 3 – Race condition gjatë broadcast
**Problemi:** Iterimi i `members` set ndërkohë që një thread tjetër thirri `removeMember()` shkaktonte `ConcurrentModificationException`.  
**Zgjidhja:** Merr snapshot të listës brenda lock-ut, pastaj bëj broadcast jashtë lock-ut (I/O nuk mban lock).

### Problemi 4 – Përditësimi i UI nga thread tjetër
**Problemi:** `ReceiverThread` provoi të azhornonte `ListView` drejtpërdrejt → `IllegalStateException` ("Not on FX application thread").  
**Zgjidhja:** Çdo ndryshim i UI mbështillet me `Platform.runLater()`.

---

## Lista e testimit

- [x] 3+ klientë të lidhur njëkohësisht
- [x] Username i dyfishtë refuzohet me mesazh gabimi
- [x] Mesazhet shpërndahen vetëm tek anëtarët e room-it
- [x] Njoftime join/leave dërgohen në kohë reale (push-based)
- [x] Shkëputja e klientit e largon nga room dhe nga mapa
- [x] Nuk humbet asnjë mesazh nën ngarkesë paralele

---

## Struktura e dosjeve

```
distributed-chat/
├── pom.xml
├── README.md
└── src/main/java/
    ├── shared/
    │   ├── CommandType.java
    │   └── Packet.java
    ├── server/
    │   ├── ChatServer.java
    │   ├── ClientHandler.java
    │   ├── ChatRoom.java
    │   └── RoomManager.java
    └── client/
        └── ChatClientApp.java
```
# Distributed Chat System
### Java · Sockets · Multithreading · BlockingQueue · JavaFX

---

## Udhezimet per ekzekutim

### Kerkesat paraprake
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
Serveri starton ne **port 5000** dhe krijon automatikisht room-in `General`.  
Per port te ndryshem:
```bash
java -cp target/distributed-chat-1.0-SNAPSHOT-server.jar server.ChatServer 6000
```

### Hapi 3 – Nisja e Klienteve (minimum 3)
```bash
mvn javafx:run
```
Cdo klient hap dritaren e login-it. Fut `localhost`, `5000`, dhe nje username unik, pastaj kliko **Connect**.

> **Testimi me 3 kliente:** Hap 3 terminale të ndara dhe ekzekuto `mvn javafx:run` ne secilin.

---

## Pershkrim i arkitektures

### Pamje e pergjithshme

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
│  JavaFX Application Thread  ── UI                      │
│  ReceiverThread              ── bllokon mbi ObjectInputStream   │
│                                 Platform.runLater() per UI     │
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

| Klasa | Paketa | Roli                                                 |
|-------|--------|------------------------------------------------------|
| `ChatServer` | `server` | ServerSocket + ThreadPool + regjistri i klienteve    |
| `ClientHandler` | `server` | Menaxhon nje klient ne thread te vecante (Runnable)  |
| `ChatRoom` | `server` | BlockingQueue + DispatcherThread + lista e anetareve |
| `RoomManager` | `server` | Regjistri i te gjitha room-eve (ConcurrentHashMap)   |
| `Packet` | `shared` | Mesazhi serializable i protokollit (Builder pattern) |
| `CommandType` | `shared` | Enum me te gjitha komandat e protokollit             |
| `ChatClientApp` | `client` | JavaFX GUI + ReceiverThread                          |

---

## Kerkesat teknike – si jane plotësuar

### 1. Multithreading
- `ChatServer` perdore `ExecutorService` (`CachedThreadPool`) — ku cdo klient e merr thread-in e vet
- `ChatRoom` ka `DispatcherThread` te dedikuar per cdo room
- Klienti ka `ReceiverThread` te vecante per marrjen e mesazheve pa bllokuar UI

### 2. BlockingQueue
- `ChatRoom` përdor `LinkedBlockingQueue<Packet>`
- `ClientHandler.handleSendMessage()` → `enqueueMessage()` → fut ne queue
- `DispatcherThread.dispatchLoop()` → `queue.take()` bllokon pa harxhuar CPU, zgjohet vetem kur vjen mesazh
-  **push-based system** — nuk ka polling

### 3. Sinkronizimi
| Struktura                              | Mbrojtja                                         |
|----------------------------------------|--------------------------------------------------|
| `connectedClients` (Map ne ChatServer) | `ReentrantLock` — `clientsLock`                  |
| `members` (Set ne ChatRoom)            | `ReentrantLock` — `membersLock`                  |
| Room map (RoomManager)                 | `ConcurrentHashMap` — thread-safe pa lock shtese |
| `ObjectOutputStream` (ClientHandler)   | `synchronized` ne `sendPacket()`                 |

**Snapshot pattern** — brenda `broadcast()`, lista e anetarëve kopjohet nen lock, pastaj lock-u lirohet **para** I/O. Kjo parandalon bllokim te gjate dhe `ConcurrentModificationException`.

### 4. Programimi në rrjet
- `ServerSocket` ne `ChatServer.start()`
- `Socket` per cdo klient te lidhur
- `ObjectOutputStream` / `ObjectInputStream` per serializim te `Packet`-ave

### 5. Protokolli i komunikimit
Cdo mesazh eshte objekt `Packet implements Serializable` me fushat:
- `CommandType type` — lloji i komandes
- `String sender` — derguesi
- `String roomName` — room-i
- `String content` — permbajtja
- `LocalDateTime timestamp` — ora e dergimit (vendoset automatikisht)
- `List<String> data` — payload per lista (rooms, users)

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

- [x] 3+ kliente te lidhur njekohesisht
- [x] Username i dyfishte refuzohet me mesazh gabimi
- [x] Mesazhet shperndahen vetëm tek anetaret e room-it
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
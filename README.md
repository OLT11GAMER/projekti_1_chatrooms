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
Cdo klient hap dritaren e login-it. Fut `localhost`, `12345`, dhe nje username unik, pastaj kliko **Connect**.

> **Testimi me 3 kliente:** Hap 3 terminale te ndara dhe ekzekuto `mvn javafx:run` ne secilin.

---

## Pershkrim i arkitektures

### Pamje e pergjithshme

<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 900 680" width="900" height="680" font-family="Arial, sans-serif">
<title>Distributed Chat System Architecture Diagram</title>

<defs>
  <marker id="arr" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
    <path d="M2 1L8 5L2 9" fill="none" stroke="context-stroke" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
  </marker>
</defs>

<rect width="900" height="680" fill="#f8f9fa"/>
<text x="450" y="36" text-anchor="middle" font-size="18" font-weight="bold" fill="#1a202c">Distributed Chat System – Architecture Diagram</text>

<!-- ═══ CLIENTS ═══ -->
<rect x="20" y="70" width="150" height="70" rx="10" fill="#e6f4ef" stroke="#1D9E75" stroke-width="1.5"/>
<text x="95" y="98"  text-anchor="middle" font-size="13" font-weight="bold" fill="#0F6E56">Client A</text>
<text x="95" y="114" text-anchor="middle" font-size="11" fill="#0F6E56">JavaFX UI</text>
<text x="95" y="128" text-anchor="middle" font-size="10" fill="#085041">ReceiverThread · FX Thread</text>

<rect x="20" y="165" width="150" height="70" rx="10" fill="#e6f4ef" stroke="#1D9E75" stroke-width="1.5"/>
<text x="95" y="193" text-anchor="middle" font-size="13" font-weight="bold" fill="#0F6E56">Client B</text>
<text x="95" y="209" text-anchor="middle" font-size="11" fill="#0F6E56">JavaFX UI</text>
<text x="95" y="223" text-anchor="middle" font-size="10" fill="#085041">ReceiverThread · FX Thread</text>

<rect x="20" y="260" width="150" height="70" rx="10" fill="#e6f4ef" stroke="#1D9E75" stroke-width="1.5"/>
<text x="95" y="288" text-anchor="middle" font-size="13" font-weight="bold" fill="#0F6E56">Client C</text>
<text x="95" y="304" text-anchor="middle" font-size="11" fill="#0F6E56">JavaFX UI</text>
<text x="95" y="318" text-anchor="middle" font-size="10" fill="#085041">ReceiverThread · FX Thread</text>

<!-- TCP arrows -->
<line x1="170" y1="105" x2="240" y2="152" stroke="#1D9E75" stroke-width="1.5" marker-end="url(#arr)"/>
<line x1="170" y1="200" x2="240" y2="200" stroke="#1D9E75" stroke-width="1.5" marker-end="url(#arr)"/>
<line x1="170" y1="295" x2="240" y2="252" stroke="#1D9E75" stroke-width="1.5" marker-end="url(#arr)"/>
<text x="196" y="192" text-anchor="middle" font-size="10" fill="#0F6E56">TCP</text>
<text x="196" y="204" text-anchor="middle" font-size="10" fill="#0F6E56">Socket</text>

<!-- ═══ SERVER OUTER BOX ═══ -->
<rect x="238" y="50" width="645" height="460" rx="16" fill="none" stroke="#a0aec0" stroke-width="1.5" stroke-dasharray="8 4"/>
<text x="560" y="76" text-anchor="middle" font-size="14" font-weight="bold" fill="#2d3748">Chat Server  (port 5000)</text>

<!-- ServerSocket -->
<rect x="252" y="88" width="150" height="52" rx="8" fill="#ebf4ff" stroke="#185FA5" stroke-width="1.2"/>
<text x="327" y="110" text-anchor="middle" font-size="12" font-weight="bold" fill="#0C447C">ServerSocket</text>
<text x="327" y="126" text-anchor="middle" font-size="10" fill="#185FA5">accept() loop</text>
<line x1="327" y1="140" x2="327" y2="164" stroke="#185FA5" stroke-width="1.2" marker-end="url(#arr)"/>

<!-- Thread Pool -->
<rect x="252" y="164" width="150" height="62" rx="8" fill="#ebf4ff" stroke="#185FA5" stroke-width="1.2"/>
<text x="327" y="186" text-anchor="middle" font-size="12" font-weight="bold" fill="#0C447C">Thread Pool</text>
<text x="327" y="202" text-anchor="middle" font-size="10" fill="#185FA5">CachedThreadPool</text>
<text x="327" y="216" text-anchor="middle" font-size="10" fill="#185FA5">1 thread per client</text>
<line x1="327" y1="226" x2="327" y2="248" stroke="#888" stroke-width="1.2" marker-end="url(#arr)"/>

<!-- Client Registry -->
<rect x="252" y="248" width="150" height="52" rx="8" fill="#f7f7f7" stroke="#888" stroke-width="1.2"/>
<text x="327" y="270" text-anchor="middle" font-size="12" font-weight="bold" fill="#444">Client Registry</text>
<text x="327" y="286" text-anchor="middle" font-size="10" fill="#666">ReentrantLock ①</text>
<line x1="327" y1="300" x2="327" y2="322" stroke="#888" stroke-width="1.2" marker-end="url(#arr)"/>

<!-- RoomManager -->
<rect x="252" y="322" width="150" height="52" rx="8" fill="#f7f7f7" stroke="#888" stroke-width="1.2"/>
<text x="327" y="344" text-anchor="middle" font-size="12" font-weight="bold" fill="#444">RoomManager</text>
<text x="327" y="360" text-anchor="middle" font-size="10" fill="#666">ConcurrentHashMap ②</text>

<!-- Dashed connectors from RoomManager to both rooms -->
<line x1="402" y1="348" x2="436" y2="200" stroke="#888" stroke-width="1" stroke-dasharray="4 3"/>
<line x1="402" y1="348" x2="436" y2="348" stroke="#888" stroke-width="1" marker-end="url(#arr)"/>

<!-- ═══ CHAT ROOM GENERAL ═══ -->
<rect x="436" y="88" width="430" height="175" rx="12" fill="#f3f0fe" stroke="#534AB7" stroke-width="1.5"/>
<text x="651" y="112" text-anchor="middle" font-size="13" font-weight="bold" fill="#3C3489"># General  (ChatRoom)</text>

<rect x="450" y="122" width="175" height="46" rx="7" fill="#fff8e6" stroke="#BA7517" stroke-width="1.2"/>
<text x="537" y="141" text-anchor="middle" font-size="11" font-weight="bold" fill="#854F0B">BlockingQueue ③</text>
<text x="537" y="157" text-anchor="middle" font-size="10" fill="#854F0B">LinkedBlockingQueue</text>
<line x1="537" y1="168" x2="537" y2="188" stroke="#BA7517" stroke-width="1.2" marker-end="url(#arr)"/>

<rect x="450" y="188" width="175" height="46" rx="7" fill="#fde8e0" stroke="#993C1D" stroke-width="1.2"/>
<text x="537" y="207" text-anchor="middle" font-size="11" font-weight="bold" fill="#712B13">DispatcherThread ④</text>
<text x="537" y="222" text-anchor="middle" font-size="10" fill="#712B13">take() → broadcast()</text>
<line x1="625" y1="211" x2="638" y2="211" stroke="#993C1D" stroke-width="1.2" marker-end="url(#arr)"/>

<rect x="638" y="122" width="214" height="112" rx="7" fill="#f7f7f7" stroke="#888" stroke-width="1.2"/>
<text x="745" y="144" text-anchor="middle" font-size="11" font-weight="bold" fill="#444">Members  (Set&lt;ClientHandler&gt;)</text>
<text x="745" y="162" text-anchor="middle" font-size="10" fill="#666">ReentrantLock ①  –  snapshot pattern</text>
<text x="745" y="178" text-anchor="middle" font-size="10" fill="#666">lock → copy list → unlock → I/O</text>
<text x="745" y="196" text-anchor="middle" font-size="10" fill="#666">sendMessage() synchronized ⑤</text>
<text x="745" y="214" text-anchor="middle" font-size="10" fill="#999">→ ObjectOutputStream per client</text>

<!-- ═══ CHAT ROOM RANDOM ═══ -->
<rect x="436" y="282" width="430" height="175" rx="12" fill="#f3f0fe" stroke="#534AB7" stroke-width="1.5"/>
<text x="651" y="306" text-anchor="middle" font-size="13" font-weight="bold" fill="#3C3489"># Random  (ChatRoom)</text>

<rect x="450" y="316" width="175" height="46" rx="7" fill="#fff8e6" stroke="#BA7517" stroke-width="1.2"/>
<text x="537" y="335" text-anchor="middle" font-size="11" font-weight="bold" fill="#854F0B">BlockingQueue ③</text>
<text x="537" y="351" text-anchor="middle" font-size="10" fill="#854F0B">LinkedBlockingQueue</text>
<line x1="537" y1="362" x2="537" y2="382" stroke="#BA7517" stroke-width="1.2" marker-end="url(#arr)"/>

<rect x="450" y="382" width="175" height="46" rx="7" fill="#fde8e0" stroke="#993C1D" stroke-width="1.2"/>
<text x="537" y="401" text-anchor="middle" font-size="11" font-weight="bold" fill="#712B13">DispatcherThread ④</text>
<text x="537" y="416" text-anchor="middle" font-size="10" fill="#712B13">take() → broadcast()</text>
<line x1="625" y1="405" x2="638" y2="405" stroke="#993C1D" stroke-width="1.2" marker-end="url(#arr)"/>

<rect x="638" y="316" width="214" height="112" rx="7" fill="#f7f7f7" stroke="#888" stroke-width="1.2"/>
<text x="745" y="338" text-anchor="middle" font-size="11" font-weight="bold" fill="#444">Members  (Set&lt;ClientHandler&gt;)</text>
<text x="745" y="356" text-anchor="middle" font-size="10" fill="#666">ReentrantLock ①  –  snapshot pattern</text>
<text x="745" y="372" text-anchor="middle" font-size="10" fill="#666">lock → copy list → unlock → I/O</text>
<text x="745" y="390" text-anchor="middle" font-size="10" fill="#666">sendMessage() synchronized ⑤</text>
<text x="745" y="408" text-anchor="middle" font-size="10" fill="#999">→ ObjectOutputStream per client</text>

<!-- ═══ LEGEND ═══ -->
<text x="450" y="526" text-anchor="middle" font-size="10" fill="#888">① ReentrantLock   ② ConcurrentHashMap   ③ LinkedBlockingQueue   ④ Dispatcher thread   ⑤ synchronized write</text>

<!-- ═══ MESSAGE FLOW SECTION ═══ -->
<rect x="20" y="538" width="860" height="126" rx="12" fill="none" stroke="#a0aec0" stroke-width="1.2" stroke-dasharray="6 4"/>
<text x="450" y="558" text-anchor="middle" font-size="13" font-weight="bold" fill="#2d3748">Rrjedha e mesazhit (Message Flow)</text>

<!-- Step 1: Client -->
<rect x="32" y="568" width="108" height="52" rx="8" fill="#e6f4ef" stroke="#1D9E75" stroke-width="1.2"/>
<text x="86" y="589" text-anchor="middle" font-size="11" font-weight="bold" fill="#0F6E56">① Klienti</text>
<text x="86" y="605" text-anchor="middle" font-size="10" fill="#0F6E56">SEND_MESSAGE</text>
<line x1="140" y1="594" x2="160" y2="594" stroke="#666" stroke-width="1.2" marker-end="url(#arr)"/>

<!-- Step 2: ClientHandler -->
<rect x="160" y="568" width="122" height="52" rx="8" fill="#ebf4ff" stroke="#185FA5" stroke-width="1.2"/>
<text x="221" y="589" text-anchor="middle" font-size="11" font-weight="bold" fill="#0C447C">② ClientHandler</text>
<text x="221" y="605" text-anchor="middle" font-size="10" fill="#185FA5">enqueue()</text>
<line x1="282" y1="594" x2="302" y2="594" stroke="#666" stroke-width="1.2" marker-end="url(#arr)"/>

<!-- Step 3: BlockingQueue -->
<rect x="302" y="568" width="132" height="52" rx="8" fill="#fff8e6" stroke="#BA7517" stroke-width="1.2"/>
<text x="368" y="589" text-anchor="middle" font-size="11" font-weight="bold" fill="#854F0B">③ BlockingQueue</text>
<text x="368" y="605" text-anchor="middle" font-size="10" fill="#854F0B">offer() / take()</text>
<line x1="434" y1="594" x2="454" y2="594" stroke="#666" stroke-width="1.2" marker-end="url(#arr)"/>

<!-- Step 4: DispatcherThread -->
<rect x="454" y="568" width="150" height="52" rx="8" fill="#fde8e0" stroke="#993C1D" stroke-width="1.2"/>
<text x="529" y="589" text-anchor="middle" font-size="11" font-weight="bold" fill="#712B13">④ DispatcherThread</text>
<text x="529" y="605" text-anchor="middle" font-size="10" fill="#712B13">take() → broadcast()</text>
<line x1="604" y1="594" x2="624" y2="594" stroke="#666" stroke-width="1.2" marker-end="url(#arr)"/>

<!-- Step 5: Members -->
<rect x="624" y="568" width="130" height="52" rx="8" fill="#f3f0fe" stroke="#534AB7" stroke-width="1.2"/>
<text x="689" y="589" text-anchor="middle" font-size="11" font-weight="bold" fill="#3C3489">⑤ Anëtarët</text>
<text x="689" y="605" text-anchor="middle" font-size="10" fill="#534AB7">sendMessage() sync</text>
<line x1="754" y1="594" x2="774" y2="594" stroke="#1D9E75" stroke-width="1.2" stroke-dasharray="4 3" marker-end="url(#arr)"/>

<!-- Return label -->
<text x="832" y="589" text-anchor="middle" font-size="10" fill="#0F6E56">→ Klientët</text>
<text x="832" y="604" text-anchor="middle" font-size="10" fill="#085041">Platform.runLater</text>

</svg>

### Rrjedha e mesazhit (Message Flow)
```
Klienti          ClientHandler        BlockingQueue      DispatcherThread     Anetaret
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

## Kerkesat teknike – si jane plotesuar

### 1. Multithreading
- `ChatServer` perdore `ExecutorService` (`CachedThreadPool`) — ku cdo klient e merr thread-in e vet
- `ChatRoom` ka `DispatcherThread` te dedikuar per cdo room
- Klienti ka `ReceiverThread` te vecante per marrjen e mesazheve pa bllokuar UI

### 2. BlockingQueue
- `ChatRoom` perdor `LinkedBlockingQueue<Packet>`
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

**Snapshot pattern** — brenda `broadcast()`, lista e anetareve kopjohet nen lock, pastaj lock-u lirohet **para** I/O. Kjo parandalon bllokim te gjate dhe `ConcurrentModificationException`.

### 4. Programimi ne rrjet
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
**Problemi:** Te dy anet ndertuan `ObjectInputStream` para `ObjectOutputStream`, duke shkaktuar bllok te ndersjelle (secila priste header-in e streamit te tjetres).  
**Zgjidhja:** Gjithmone ndertoje `ObjectOutputStream` se pari dhe thirre `flush()`, pastaj `ObjectInputStream`.

### Problemi 2 – Cache i vjeter i serializimit
**Problemi:** Objekte te modifikuara dhe te derguara shume here shfaqeshin me vlerat e vjetra sepse `ObjectOutputStream` ruan cache te referencave.  
**Zgjidhja:** Thirre `out.reset()` pas çdo `writeObject()`.

### Problemi 3 – Race condition gjate broadcast
**Problemi:** Iterimi i `members` set nderkohe qe nje thread tjeter thirri `removeMember()` shkaktonte `ConcurrentModificationException`.  
**Zgjidhja:** Merr snapshot te listes brenda lock-ut, pastaj bej broadcast jashte lock-ut (I/O nuk mban lock).

### Problemi 4 – Perditesimi i UI nga thread tjeter
**Problemi:** `ReceiverThread` provoi te azhornonte `ListView` drejtperdrejte → `IllegalStateException` ("Not on FX application thread").  
**Zgjidhja:** Cdo ndryshim i UI mbeshtillet me `Platform.runLater()`.

---

## Lista e testimit

- [x] 3+ kliente te lidhur njekohesisht
- [x] Username i dyfishte refuzohet me mesazh gabimi
- [x] Mesazhet shperndahen vetem tek anetaret e room-it
- [x] Njoftime join/leave dergohen ne kohe reale (push-based)
- [x] Shkeputja e klientit e largon nga room dhe nga map
- [x] Nuk humbet asnje mesazh nen ngarkese paralele

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
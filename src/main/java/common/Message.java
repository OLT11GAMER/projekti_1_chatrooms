package common;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        //Client -> Server
        LOGIN,          // content = requested username
        CREATE_ROOM,    // roomName = desired room name
        JOIN_ROOM,      // roomName = target room
        LEAVE_ROOM,
        CHAT,           // content = message text, roomName = current room
        LIST_ROOMS,
        DISCONNECT,

        //Server -> Client
        LOGIN_SUCCESS,
        LOGIN_FAIL,
        ROOM_LIST,      // dataList = List<String> of room names
        USER_LIST,      // dataList = List<String> of usernames in room
        CHAT_BROADCAST, // sender, content, timestamp, roomName all populated
        SERVER_NOTICE,  // informational text (join/leave announcements)
        ERROR,
        ROOM_JOINED,    // roomName = the room that was joined
        ROOM_LEFT
    }

    private final Type type;
    private final String sender;
    private final String content;
    private final String roomName;
    private final String timestamp;
    private List<String> dataList; // used for ROOM_LIST / USER_LIST payloads

    public Message(Type type, String sender, String content, String roomName) {
        this.type      = type;
        this.sender    = sender;
        this.content   = content;
        this.roomName  = roomName;
        this.timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    // Getters
    public Type         getType()      { return type;      }
    public String       getSender()    { return sender;    }
    public String       getContent()   { return content;   }
    public String       getRoomName()  { return roomName;  }
    public String       getTimestamp() { return timestamp; }
    public List<String> getDataList()  { return dataList;  }
    public void         setDataList(List<String> dataList) { this.dataList = dataList; }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + type + " | " + sender + " | " + content;
    }
}

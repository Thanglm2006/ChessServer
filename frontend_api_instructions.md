# Frontend API Instructions - Chess Application

This document provides instructions for the frontend implementation to interact with the newly added backend APIs and WebSocket features.

## 1. REST APIs

### 1.1 Home API: User Profile & Stats
**Endpoint:** `GET /api/user/me`
* Returns the current logged-in user's stats based on the JWT token.

**Endpoint:** `GET /api/user/{id}/stats`
**Response:**
```json
{
  "userId": 1,
  "username": "thanglm",
  "countryCode": "VN",
  "rating": 1200,
  "gamesPlayed": 10,
  "wins": 5,
  "losses": 3,
  "draws": 2
}
```

### 1.2 Game History
**Endpoint:** `GET /api/game/history?userId={id}&page=0&size=10`
**Response:** Array of games:
```json
[
  {
    "gameId": 12,
    "opponentId": 3,
    "opponentName": "John",
    "myColor": "WHITE",
    "result": "1-0",
    "pgn": "1. e4 e5 ...",
    "playedAt": "2026-05-05T10:00:00Z"
  }
]
```

### 1.3 Friend System
**Endpoint:** `GET /api/friends/list?userId={id}`
**Response:**
```json
[
  {
    "userId": 2,
    "username": "alice",
    "status": "ONLINE",
    "rating": 1250
  }
]
```
**Endpoint:** `POST /api/friends/request?senderId={id}&receiverId={id}`
**Response:** `Friend request sent` (String)

**Endpoint:** `POST /api/friends/accept?user1={id}&user2={id}`
**Response:** `Friend request accepted` (String)

**Error Response (Global):**
```json
{
  "message": "Error description here"
}
```

## 2. Matchmaking & Game Flow

### 2.1 Matchmaking Logic
1. **Join Queue**: `POST /api/matchmaking/join?userId={id}`.
2. **Find Match**: Server pairs two users.
3. **Prepare Game**: Both users receive `PREPARE_GAME` WebSocket event.
   ```json
   {
     "type": "PREPARE_GAME",
     "gameId": "uuid",
     "opponentId": 2,
     "opponentName": "Alice",
     "opponentCountry": "US",
     "opponentRating": 1300,
     "timeout": 10
   }
   ```
4. **Ready Check**: Client must send `READY` within the timeout.
   `{"type": "READY", "gameId": "uuid"}`
5. **Game Start**: Once both are ready, `GAME_START` is sent.

### 2.2 Game Logic Flow
1. **Initial State**: Users receive `GAME_START`.
   ```json
   {
     "type": "GAME_START",
     "gameId": "uuid",
     "side": "WHITE",
     "opponent": 2
   }
   ```
2. **Making a Move**:
   `{"type": "MOVE", "gameId": "uuid", "move": "e2e4"}`
3. **Validation**: Server validates move via `chesslib`. If invalid, sends `ERROR`.
4. **Broadcast**: Opponent receives `OPPONENT_MOVE`.
   ```json
   {
     "type": "OPPONENT_MOVE",
     "move": "e2e4",
     "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
     "timeRemaining": 595
   }
   ```
5. **End Game**: When checkmate, draw, or resignation occurs, `GAME_OVER` is sent.
   ```json
   {
     "type": "GAME_OVER",
     "result": "1-0",
     "reason": "CHECKMATE"
   }
   ```

## 3. WebSocket Events

All WebSocket messages are sent and received as JSON strings over `ws://<domain>/ws/chess?token=<JWT_TOKEN>`.

### 2.1 Private Rooms
- **To Create a Room:**
  Send: `{"type": "CREATE_ROOM"}`
  Receive: `{"type": "ROOM_CREATED", "code": "A7B9"}`
  *The frontend should display this code to the user to share.*
- **To Join a Room:**
  Send: `{"type": "JOIN_ROOM", "code": "A7B9"}`
  Receive: If successful, you will receive `{"type": "GAME_START", "side": "BLACK", ...}`. If error, `{"type": "ERROR", "message": "..."}`.

### 2.2 Direct Friend Invitation
- **To Invite an Online Friend:**
  Send: `{"type": "INVITE_FRIEND", "friendId": 2}`
- **Receiving an Invitation:**
  The friend will receive: `{"type": "MATCH_INVITE", "hostId": 1, "hostName": "thanglm"}`
- **To Accept Invitation:**
  Send: `{"type": "ACCEPT_INVITE", "hostId": 1}`
  *If successful, the server will start the match and broadcast `GAME_START`.*

### 2.3 Rematch
- **To Offer Rematch:**
  Send: `{"type": "REMATCH_OFFER", "gameId": "<uuid>"}`
- **Receiving Rematch Offer:**
  You will receive: `{"type": "REMATCH_OFFERED", "gameId": "<uuid>"}`
- **To Accept/Reject Rematch:**
  Send: `{"type": "REMATCH_RESPONSE", "gameId": "<uuid>", "accepted": true}`
  *If accepted, the server will switch colors and send a `GAME_START` event.*

### 2.3 In-Game Chat
- **To Send Message:**
  Send: `{"type": "CHAT_MESSAGE", "gameId": "<uuid>", "text": "Hello!"}`
- **To Receive Message:**
  Listen for: `{"type": "CHAT_MESSAGE", "senderId": 1, "senderName": "thanglm", "text": "Hello!"}`

### 2.4 Timers
- Every `OPPONENT_MOVE` event will now include the opponent's `timeRemaining` (in seconds).
- The `RECONNECT_GAME` event will include `timeWhite` and `timeBlack` in seconds.
- The frontend should implement a local countdown timer that synchronizes with these remaining times after every turn.

## 3. General Frontend Recommendations
- Implement a countdown clock locally. Decrease the current player's time by 1 every second.
- On reconnection, restore chat history using the `chatHistory` array provided in the `RECONNECT_GAME` payload.

### 3.2 Game Replay (History)
- **Logic Location**: The **Frontend** should handle the replay logic.
- **Workflow**:
  1. Fetch the `pgn` string using the `GET /api/game/history` endpoint.
  2. Load the PGN into a local chess engine instance (e.g., `chess.js` for Web or `chesslib` for Android/Java).
  3. The user can then navigate the move list locally without any further server calls. This ensures a fast, responsive experience when scrubbing through old games.

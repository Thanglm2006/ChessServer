#!/usr/bin/env python3
import requests
import json
import time
import random
import string
import threading
import sys
import websocket

# Configuration
API_BASE = "https://chess.caelestial.store/api"
WS_BASE = "wss://chess.caelestial.store/ws"
AI_API_BASE = "http://localhost:3210/api"
SECRET_CODE = "ADMIN_SECRET_KEY_2026"
TOURNAMENT_ID = 16

BOT_BASE_NAMES = [
    "MinhDuc", "ThanhTung", "HoangNam", "QuangHuy", "GiaBach",
    "DucManh", "TuanAnh", "VanPhong", "NgocLinh", "PhuongNam",
    "HaiDang", "HoangLong", "VietAnh", "BaoLam", "QuocAnh"
]

class ChessBotClient:
    def __init__(self, username, email, password):
        self.username = username
        self.email = email
        self.password = password
        self.token = None
        self.ws = None
        
        # Game State
        self.game_id = None
        self.side = None  # "WHITE" or "BLACK"
        self.current_fen = None
        self.last_move = ""
        self.active_pairing_id = None
        self.is_running = True
        self.model = "model_ep1"  # Default model key from tournament_simulation.py

    def log(self, msg):
        print(f"[{self.username}] {msg}", flush=True)

    def start(self):
        # 1. Create Bot Account
        create_payload = {
            "username": self.username,
            "email": self.email,
            "password": self.password,
            "countryCode": "VN",
            "secretCode": SECRET_CODE
        }
        try:
            res = requests.post(f"{API_BASE}/auth/create-bot-user", json=create_payload)
            if res.status_code == 200:
                self.log("Account created successfully.")
            elif "already exists" in res.text.lower():
                self.log("Account already exists, proceeding to login.")
            else:
                self.log(f"Failed to create bot: {res.status_code} - {res.text}")
                return
        except Exception as e:
            self.log(f"Connection error during registration: {e}")
            return

        # 2. Login
        login_payload = {"email": self.email, "password": self.password}
        try:
            res = requests.post(f"{API_BASE}/auth/login", json=login_payload)
            if res.status_code == 200:
                data = res.json()
                self.token = data.get("accessToken") or data.get("token")
                self.log("Logged in successfully.")
            else:
                self.log(f"Login failed: {res.status_code} - {res.text}")
                return
        except Exception as e:
            self.log(f"Connection error during login: {e}")
            return

        # 3. Join/Register Tournament
        try:
            join_headers = {"Authorization": f"Bearer {self.token}"}
            res_join = requests.post(f"{API_BASE}/tournaments/{TOURNAMENT_ID}/join", json={}, headers=join_headers)
            if res_join.status_code == 200:
                self.log(f"Registered/Joined tournament {TOURNAMENT_ID} successfully.")
            elif "already registered" in res_join.text.lower():
                self.log(f"Already registered for tournament {TOURNAMENT_ID}.")
            else:
                self.log(f"Failed to join tournament: {res_join.status_code} - {res_join.text}")
        except Exception as e:
            self.log(f"Connection error during joining tournament: {e}")

        # 4. Start WebSocket Thread
        ws_thread = threading.Thread(target=self.run_ws_loop, daemon=True)
        ws_thread.start()

        # 5. Start Lobby Polling Thread
        poll_thread = threading.Thread(target=self.run_lobby_polling_loop, daemon=True)
        poll_thread.start()

    def run_ws_loop(self):
        while self.is_running:
            self.log(f"Connecting to WebSocket: {WS_BASE}")
            try:
                ws_url = f"{WS_BASE}?token={self.token}"
                self.ws = websocket.WebSocketApp(
                    ws_url,
                    on_message=self.on_message,
                    on_error=self.on_error,
                    on_close=self.on_close,
                    on_open=self.on_open
                )
                self.ws.run_forever()
            except Exception as e:
                self.log(f"WebSocket execution error: {e}")
            
            # Reconnect delay
            time.sleep(5)

    def on_open(self, ws):
        self.log("WebSocket connection established.")

    def on_close(self, ws, *args, **kwargs):
        self.log(f"WebSocket closed. Args: {args}, Kwargs: {kwargs}")
        self.ws = None

    def on_error(self, ws, error):
        self.log(f"WebSocket error: {error}")

    def on_message(self, ws, message):
        try:
            data = json.loads(message)
            msg_type = data.get("type")
            
            if msg_type == "TOURNAMENT_LOBBY_UPDATE":
                self.log(f"Lobby Update: White Ready: {data.get('whiteReady')}, Black Ready: {data.get('blackReady')}")
                
            elif msg_type == "GAME_START":
                self.game_id = data.get("gameId")
                self.side = data.get("side") # "WHITE" or "BLACK"
                self.current_fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                self.last_move = ""
                self.log(f"⚔️ Game Started! GameID: {self.game_id} | Side: {self.side}")
                
                # If White, bot moves first
                if self.side == "WHITE":
                    self.make_ai_move()

            elif msg_type == "OPPONENT_MOVE":
                opp_move = data.get("move")
                new_fen = data.get("fen")
                self.log(f"Opponent moved: {opp_move}")
                self.current_fen = new_fen
                self.last_move = opp_move
                
                # Trigger bot turn
                self.make_ai_move()

            elif msg_type == "GAME_OVER":
                self.log(f"🏁 Game Over. Result: {data.get('result')} | Reason: {data.get('reason')}")
                # Reset game states
                self.game_id = None
                self.side = None
                self.current_fen = None
                self.last_move = ""
                self.active_pairing_id = None
                
            elif msg_type == "ERROR":
                self.log(f"⚠️ Server error message: {data.get('message')}")
                
        except Exception as e:
            self.log(f"Error parsing WebSocket message: {e}")

    def make_ai_move(self):
        if not self.game_id or not self.current_fen:
            return

        # Check turn based on FEN
        is_white_turn = " w " in self.current_fen
        if (self.side == "WHITE" and not is_white_turn) or (self.side == "BLACK" and is_white_turn):
            # Not our turn
            return

        self.log("Thinking of next move...")
        
        # Request move from FastAPI
        try:
            payload = {
                "fen": self.current_fen,
                "player_move": self.last_move,
                "model": self.model,
                "difficulty": 3
            }
            res = requests.post(f"{AI_API_BASE}/move", json=payload, timeout=10)
            if res.status_code != 200:
                self.log(f"AI Server returned error {res.status_code}: {res.text}")
                return
                
            move_data = res.json()
            ai_move_uci = move_data.get("ai_move") or move_data.get("ai_move_san")
            new_fen = move_data.get("fen")
            
            if not ai_move_uci:
                self.log("AI returned empty move. Cannot execute.")
                return

            self.log(f"Selected Move (UCI): {ai_move_uci}")

            # Send move to backend via WS
            move_payload = {
                "type": "MOVE",
                "gameId": self.game_id,
                "move": ai_move_uci
            }
            if self.ws and getattr(self.ws, 'sock', None) is not None:
                self.ws.send(json.dumps(move_payload))
                self.current_fen = new_fen
                self.last_move = ai_move_uci
            else:
                self.log("WebSocket is disconnected. Cannot send move.")

        except Exception as e:
            self.log(f"Error communicating with AI FastAPI Server: {e}")

    def run_lobby_polling_loop(self):
        """Poll the tournament pairing endpoint periodically to check if we are paired.
        If we are paired and not ready, send a join lobby message."""
        while self.is_running:
            if not self.token:
                time.sleep(2)
                continue

            # Only poll if we are NOT currently in an active game
            if not self.game_id:
                try:
                    headers = {"Authorization": f"Bearer {self.token}"}
                    res = requests.get(f"{API_BASE}/tournaments/{TOURNAMENT_ID}/my-pairing", headers=headers, timeout=5)
                    if res.status_code == 200:
                        # Handle case where server returns 200 OK but empty body (no pairing yet)
                        if not res.text.strip():
                            time.sleep(5)
                            continue
                            
                        pairing = res.json()
                        p_id = pairing.get("pairingId")
                        
                        if p_id:
                            # Verify if we are already ready in this pairing
                            is_white = pairing.get("whitePlayerId") == pairing.get("myId")
                            i_am_ready = pairing.get("whiteReady") if is_white else pairing.get("blackReady")
                            
                            if not i_am_ready:
                                self.log(f"Found active Pairing ID: {p_id}. Registering ready...")
                                join_payload = {
                                    "type": "TOURNAMENT_JOIN_LOBBY",
                                    "pairingId": p_id
                                }
                                if self.ws and getattr(self.ws, 'sock', None) is not None:
                                    self.ws.send(json.dumps(join_payload))
                                    self.active_pairing_id = p_id
                                else:
                                    self.log("WebSocket not ready yet to register lobby readiness.")
                    elif res.status_code == 400:
                        pass
                except Exception as e:
                    self.log(f"Error polling lobby status: {e}")

            time.sleep(5)

def main():
    print("=" * 60)
    print("      CHESS TOURNAMENT AUTOMATED BOT CONTROLLER")
    print("=" * 60)
    print(f"Tournament ID: {TOURNAMENT_ID}")
    print(f"Backend Server: {API_BASE}")
    print(f"FastAPI AI Server: {AI_API_BASE}\n")
    
    # Generate 5 random bots
    bots = []
    selected_names = random.sample(BOT_BASE_NAMES, 5)
    
    for base_name in selected_names:
        rand_id = ''.join(random.choices(string.digits, k=4))
        username = f"{base_name}_{rand_id}"
        email = f"{username.lower()}@vku.udn.vn"
        password = f"BotPassword#{rand_id}"
        
        bot = ChessBotClient(username, email, password)
        bots.append(bot)
        
    print(f"Starting {len(bots)} bot threads...")
    for bot in bots:
        bot.start()
        time.sleep(1)
        
    print("\nBots are running! Keep this script alive to let them play.")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nShutting down bots...")
        for bot in bots:
            bot.is_running = False
            if bot.ws:
                bot.ws.close()
        print("Goodbye!")

if __name__ == "__main__":
    main()

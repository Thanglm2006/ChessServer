#!/usr/bin/env python3
import requests
import json
import time
import sys

class Tee:
    def __init__(self, *files):
        self.files = files
    def write(self, obj):
        for f in self.files:
            f.write(obj)
            f.flush()
    def flush(self):
        for f in self.files:
            f.flush()


# Configuration
API_BASE = "http://localhost:8087/api"
AI_API_BASE = "http://localhost:3210/api"
ADMIN_EMAIL = "thanglm.24ai@vku.udn.vn"
ADMIN_PASSWORD = "Thang#2006"
SECRET_CODE = "ADMIN_SECRET_KEY_2026"

def create_bot_users():
    print("--- 1. Creating Bot Users ---")
    bots = []
    for i in range(1, 9):
        username = f"bot_ai_{i}"
        email = f"bot_ai_{i}@vku.udn.vn"
        password = "BotPassword#123"
        payload = {
            "username": username,
            "email": email,
            "password": password,
            "countryCode": "VN",
            "secretCode": SECRET_CODE
        }
        res = requests.post(f"{API_BASE}/auth/create-bot-user", json=payload)
        if res.status_code == 200:
            print(f"Created {username} successfully")
        elif "already exists" in res.text:
            print(f"{username} already exists, skipping creation")
        else:
            print(f"Failed to create {username}: {res.status_code} - {res.text}")
        bots.append({"email": email, "password": password, "username": username})
    return bots

def login_user(email, password):
    res = requests.post(f"{API_BASE}/auth/login", json={
        "email": email,
        "password": password
    })
    if res.status_code == 200:
        data = res.json()
        return data.get("accessToken") or data.get("token")
    else:
        print(f"Login failed for {email}: {res.status_code} - {res.text}")
        return None

def main():
    # 1. Create bots
    bots = create_bot_users()
    
    # 2. Get tokens
    print("\n--- 2. Logging in users ---")
    admin_token = login_user(ADMIN_EMAIL, ADMIN_PASSWORD)
    if not admin_token:
        print("Failed to login admin. Exiting.")
        sys.exit(1)
    print("Admin logged in successfully.")
    
    bot_tokens = []
    for bot in bots:
        tok = login_user(bot["email"], bot["password"])
        if tok:
            bot_tokens.append((bot["username"], tok))
    
    if len(bot_tokens) < 8:
        print(f"Not enough bots logged in ({len(bot_tokens)}/8). Exiting.")
        sys.exit(1)
        
    # 3. Fetch AI Models
    print("\n--- 3. Fetching AI Models ---")
    try:
        models_res = requests.get(f"{AI_API_BASE}/models").json()
        models = [m["key"] for m in models_res.get("models", [])]
        print(f"Available AI Models: {models}")
    except Exception as e:
        print("Failed to connect to FastAPI AI server. Using default models list.")
        models = ["model_ep1", "model_ep2", "model_ep3", "model_ep4"]
    
    # Map bot to AI model key
    bot_models = {}
    for idx, (username, _) in enumerate(bot_tokens):
        model_key = models[idx % len(models)] if models else "model_ep1"
        bot_models[username] = model_key
        print(f"Bot {username} will use model: {model_key}")
        
    # 4. Create Tournament
    print("\n--- 4. Creating Tournament ---")
    admin_headers = {"Authorization": f"Bearer {admin_token}"}
    tourney_payload = {
        "tournamentName": "AI Swiss Masters 2026",
        "description": "Simulated match-play championship between neural network checkpoints.",
        "totalRounds": 3,
        "timeControl": "10+0",
        "registrationStart": None,
        "registrationEnd": None,
        "startTime": None
    }
    create_res = requests.post(f"{API_BASE}/admin/tournaments", json=tourney_payload, headers=admin_headers)
    if create_res.status_code != 200:
        print(f"Failed to create tournament: {create_res.status_code} - {create_res.text}")
        sys.exit(1)
        
    tournament = create_res.json()
    t_id = tournament["tournamentId"]
    print(f"Created Tournament '{tournament['tournamentName']}' with ID: {t_id}")
    
    # 5. Bots Join Tournament
    print("\n--- 5. Bots Joining Tournament ---")
    for username, tok in bot_tokens:
        join_headers = {"Authorization": f"Bearer {tok}"}
        join_res = requests.post(f"{API_BASE}/tournaments/{t_id}/join", json={}, headers=join_headers)
        if join_res.status_code == 200:
            print(f"Bot {username} joined tournament")
        else:
            print(f"Bot {username} failed to join: {join_res.status_code} - {join_res.text}")
            
    # 6. Admin Starts Tournament
    print("\n--- 6. Starting Tournament ---")
    start_res = requests.post(f"{API_BASE}/admin/tournaments/{t_id}/start", json={}, headers=admin_headers)
    if start_res.status_code != 200:
        print(f"Failed to start tournament: {start_res.status_code} - {start_res.text}")
        sys.exit(1)
    print("Tournament started successfully!")

    # 7. Simulating Rounds
    for round_num in range(1, 4):
        print(f"\n=================== SIMULATING ROUND {round_num} ===================")
        # Wait a moment for pairings to generate
        time.sleep(1)
        
        # Get pairings for current round
        # To get current round, get rounds list first
        rounds_res = requests.get(f"{API_BASE}/tournaments/{t_id}/rounds", headers=admin_headers)
        if rounds_res.status_code != 200:
            # Fallback to public endpoints if needed, but since we are admin we can use the same
            rounds_res = requests.get(f"{API_BASE}/tournaments/{t_id}/rounds")
            
        rounds = rounds_res.json()
        current_round = [r for r in rounds if r["roundNumber"] == round_num]
        if not current_round:
            print(f"Could not find round {round_num} in response: {rounds}")
            break
            
        round_id = current_round[0]["roundId"]
        
        # Get pairings
        pairings_res = requests.get(f"{API_BASE}/tournaments/{t_id}/pairings/{round_id}", headers=admin_headers)
        pairings = pairings_res.json()
        print(f"Found {len(pairings)} pairings in round {round_num}:")
        
        for p in pairings:
            p_id = p["pairingId"]
            w_name = p["whitePlayerName"]
            b_name = p["blackPlayerName"]
            is_bye = p.get("isBye", False)
            
            if is_bye or b_name == "BYE" or w_name == "BYE":
                print(f"Pairing {p_id}: {w_name} gets a BYE. Auto-scoring 1-0.")
                continue
                
            print(f"\nPlaying Game: [White] {w_name} vs [Black] {b_name}")
            
            # Simulate game moves between the two models
            w_model = bot_models.get(w_name, "model_ep1")
            b_model = bot_models.get(b_name, "model_ep2")
            
            fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
            moves_history = []
            is_game_over = False
            last_move = ""
            step = 0
            
            print(f"Simulating AI play: {w_name} ({w_model}) vs {b_name} ({b_model})")
            
            while not is_game_over and step < 60: # Limit to 60 moves to avoid long runs
                current_model = w_model if " w " in fen else b_model
                
                # Request AI move from FastAPI
                try:
                    payload = {
                        "fen": fen,
                        "player_move": "",
                        "model": current_model,
                        "difficulty": 3
                    }
                    move_res = requests.post(f"{AI_API_BASE}/move", json=payload)
                    if move_res.status_code != 200:
                        print(f"FastAPI error: {move_res.status_code} - {move_res.text}")
                        break
                        
                    move_data = move_res.json()
                    ai_move_san = move_data.get("ai_move_san")
                    new_fen = move_data.get("fen")
                    is_game_over = move_data.get("is_game_over", False)
                    result = move_data.get("result")
                    
                    if not ai_move_san:
                        # Fallback
                        print("AI returned empty move, ending simulation.")
                        break
                        
                    evaluation = move_data.get("evaluation", 0.0)
                    if evaluation is None:
                        evaluation = 0.0
                        
                    moves_history.append({
                        "moveNumber": (step // 2) + 1,
                        "sanMove": ai_move_san,
                        "fenAfterMove": new_fen,
                        "evaluation": evaluation
                    })
                    
                    fen = new_fen
                    last_move = ai_move_san
                    step += 1
                except Exception as e:
                    print(f"Simulation exception: {e}")
                    break
            
            # Final game result
            final_result = "1/2-1/2"
            if is_game_over and result:
                final_result = result
            elif step >= 60:
                final_result = "1/2-1/2" # Draw by move limit
            else:
                final_result = "1-0" # Fallback to White wins
                
            # Build PGN
            pgn_parts = []
            for i, mv in enumerate(moves_history):
                if i % 2 == 0:
                    pgn_parts.append(f"{mv['moveNumber']}. {mv['sanMove']}")
                else:
                    pgn_parts.append(mv['sanMove'])
            pgn_string = " ".join(pgn_parts)
            
            print(f"Game completed in {step} steps. Result: {final_result}")
            
            # Submit pairing complete game[]
            submit_payload = {
                "result": final_result,
                "pgnData": pgn_string,
                "moves": moves_history
            }
            sub_res = requests.post(f"{API_BASE}/admin/pairings/{p_id}/complete-game", json=submit_payload, headers=admin_headers)
            if sub_res.status_code == 200:
                print(f"Simulated game for pairing {p_id} submitted successfully!")
            else:
                print(f"Failed to submit game: {sub_res.status_code} - {sub_res.text}")

    print("\n--- 8. Tournament Finished Standings ---")
    standings_res = requests.get(f"{API_BASE}/tournaments/{t_id}/standings")
    standings = standings_res.json()
    for idx, st in enumerate(standings):
        print(f"Rank {idx+1}: {st['username']} - Score: {st['currentScore']} (Buchholz: {st['buchholz']}, SB: {st['sonnebornBerger']})")

if __name__ == "__main__":
    log_file = open("tournament_simulation.log", "w", encoding="utf-8")
    sys.stdout = Tee(sys.stdout, log_file)
    sys.stderr = Tee(sys.stderr, log_file)
    try:
        main()
    finally:
        log_file.close()


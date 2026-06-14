#!/usr/bin/env python3
import requests
import random
import string
import sys

# Configuration
API_BASE = "https://chess.caelestial.store/api"
TOURNAMENT_ID = 16
SECRET_CODE = "ADMIN_SECRET_KEY_2026"

BOT_BASE_NAMES = [
    "MagnusBot", "HikaruBot", "FischerBot", "TalBot",
    "KasparovBot", "AnandBot", "CarlsenBot", "CapablancaBot",
    "AlekhineBot", "MorphyBot", "SpasskyBot", "KarpovBot",
    "PolgarBot", "NakamuraBot", "CaruanaBot"
]

def generate_random_string(length=4):
    return ''.join(random.choices(string.digits, k=length))

def run():
    print(f"Starting registration for 5 random bots to Tournament ID {TOURNAMENT_ID}...")
    print(f"Targeting Backend API: {API_BASE}\n")
    
    selected_names = random.sample(BOT_BASE_NAMES, 5)
    
    for base_name in selected_names:
        suffix = generate_random_string()
        username = f"{base_name}_{suffix}"
        email = f"{username.lower()}@vku.udn.vn"
        password = f"BotPassword#{suffix}"
        
        # 1. Create bot user
        create_payload = {
            "username": username,
            "email": email,
            "password": password,
            "countryCode": "VN",
            "secretCode": SECRET_CODE
        }
        
        print(f"Creating bot: {username} ({email})...")
        res_create = requests.post(f"{API_BASE}/auth/create-bot-user", json=create_payload)
        
        if res_create.status_code == 200:
            print(f" -> Created successfully.")
        elif "already exists" in res_create.text.lower():
            print(f" -> Bot already exists, proceeding to login.")
        else:
            print(f" -> Failed to create bot: {res_create.status_code} - {res_create.text}")
            continue
            
        # 2. Login bot
        login_payload = {
            "email": email,
            "password": password
        }
        res_login = requests.post(f"{API_BASE}/auth/login", json=login_payload)
        
        if res_login.status_code == 200:
            token_data = res_login.json()
            token = token_data.get("accessToken") or token_data.get("token")
            if not token:
                print(f" -> Error: Token not found in response: {token_data}")
                continue
            print(" -> Logged in successfully.")
        else:
            print(f" -> Login failed: {res_login.status_code} - {res_login.text}")
            continue
            
        # 3. Join tournament
        headers = {
            "Authorization": f"Bearer {token}"
        }
        res_join = requests.post(f"{API_BASE}/tournaments/{TOURNAMENT_ID}/join", json={}, headers=headers)
        
        if res_join.status_code == 200:
            print(f" -> Successfully JOINED Tournament {TOURNAMENT_ID}!")
        else:
            print(f" -> Failed to join tournament: {res_join.status_code} - {res_join.text}")
            
        print("-" * 50)
        
    print("\nAll done!")

if __name__ == "__main__":
    run()

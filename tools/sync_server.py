"""EdgeEdu account + history sync server, backed by MongoDB (PRD §17).

    pip install pymongo
    # put your MongoDB connection string in the repo .env (MONGODB_URI=…)
    python tools/sync_server.py

Implements the three JSON endpoints the app's SyncClient calls:

    POST /auth/signup   {"email","password"}            → {"token": "…"}
    POST /auth/login    {"email","password"}            → {"token": "…"}
    POST /history       Bearer <token>, [HistoryEntry…] → {"ok": true, "upserted": n}

Collections (created automatically in the SYNC_DB database):
  - users:   {email, salt, pwhash (PBKDF2-SHA256), token, created_at}
  - history: {email, client_id, asked_at, standard, medium, subject,
              question, answer, engine, received_at}
             unique index on (email, client_id) → re-uploads are idempotent.

Configuration comes from the repo-root .env (or real environment variables):
MONGODB_URI, SYNC_DB, SYNC_PORT.
"""
from __future__ import annotations

import hashlib
import json
import os
import secrets
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_env() -> dict[str, str]:
    """Environment variables, with the repo .env as fallback."""
    values: dict[str, str] = {}
    env_file = ROOT / ".env"
    if env_file.is_file():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, _, value = line.partition("=")
                values[key.strip()] = value.strip().strip('"')
    values.update({k: v for k, v in os.environ.items() if k.startswith(("MONGODB_", "SYNC_"))})
    return values


ENV = load_env()
MONGODB_URI = ENV.get("MONGODB_URI", "")
DB_NAME = ENV.get("SYNC_DB", "edgeedu")
PORT = int(ENV.get("SYNC_PORT", "8787"))

if not MONGODB_URI:
    raise SystemExit(
        "MONGODB_URI is not set. Put your MongoDB connection string in the repo .env:\n"
        "  MONGODB_URI=mongodb+srv://user:password@cluster.mongodb.net/"
    )

from pymongo import ASCENDING, MongoClient  # noqa: E402  (import after the config check)

client = MongoClient(MONGODB_URI)
db = client[DB_NAME]
db.users.create_index([("email", ASCENDING)], unique=True)
db.history.create_index([("email", ASCENDING), ("client_id", ASCENDING)], unique=True)

HISTORY_FIELDS = {"client_id", "asked_at", "standard", "medium", "subject", "question", "answer", "engine"}


def hash_password(password: str, salt: str) -> str:
    return hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), 200_000).hex()


class Handler(BaseHTTPRequestHandler):
    server_version = "EdgeEduSync/1.0"

    def send(self, code: int, payload: dict) -> None:
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def read_json(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0 or length > 10_000_000:
            return None
        try:
            return json.loads(self.rfile.read(length))
        except (json.JSONDecodeError, UnicodeDecodeError):
            return None

    def bearer(self) -> str | None:
        auth = self.headers.get("Authorization", "")
        return auth.removeprefix("Bearer ").strip() if auth.startswith("Bearer ") else None

    # ---- Routes ---------------------------------------------------------------

    def do_POST(self) -> None:  # noqa: N802 (stdlib naming)
        try:
            if self.path == "/auth/signup":
                self.handle_signup()
            elif self.path == "/auth/login":
                self.handle_login()
            elif self.path == "/history":
                self.handle_history()
            else:
                self.send(404, {"error": "not found"})
        except Exception as e:  # keep the server alive; report the failure
            self.send(500, {"error": str(e)[:200]})

    def credentials(self):
        body = self.read_json()
        if not isinstance(body, dict):
            return None, None
        email = str(body.get("email", "")).strip().lower()
        password = str(body.get("password", ""))
        if "@" not in email or len(email) < 5 or len(password) < 6:
            return None, None
        return email, password

    def handle_signup(self) -> None:
        email, password = self.credentials()
        if not email:
            return self.send(400, {"error": "valid email and 6+ character password required"})
        if db.users.find_one({"email": email}):
            return self.send(409, {"error": "an account with this email already exists — sign in instead"})
        salt = secrets.token_hex(16)
        token = secrets.token_hex(32)
        db.users.insert_one({
            "email": email,
            "salt": salt,
            "pwhash": hash_password(password, salt),
            "token": token,
            "created_at": int(time.time() * 1000),
        })
        self.send(200, {"token": token})

    def handle_login(self) -> None:
        email, password = self.credentials()
        if not email:
            return self.send(400, {"error": "valid email and 6+ character password required"})
        user = db.users.find_one({"email": email})
        if not user or hash_password(password, user["salt"]) != user["pwhash"]:
            return self.send(401, {"error": "wrong email or password"})
        self.send(200, {"token": user["token"]})

    def handle_history(self) -> None:
        token = self.bearer()
        user = db.users.find_one({"token": token}) if token else None
        if not user:
            return self.send(401, {"error": "unauthorized"})
        entries = self.read_json()
        if not isinstance(entries, list) or len(entries) > 500:
            return self.send(400, {"error": "expected a JSON array of up to 500 entries"})
        upserted = 0
        for raw in entries:
            if not isinstance(raw, dict) or "client_id" not in raw:
                continue
            doc = {k: raw[k] for k in HISTORY_FIELDS if k in raw}
            doc["email"] = user["email"]
            doc["received_at"] = int(time.time() * 1000)
            db.history.replace_one(
                {"email": user["email"], "client_id": doc["client_id"]}, doc, upsert=True
            )
            upserted += 1
        self.send(200, {"ok": True, "upserted": upserted})

    def log_message(self, format: str, *args) -> None:  # quieter default logging
        print(f"{self.address_string()} {format % args}")


if __name__ == "__main__":
    print(f"EdgeEdu sync server on http://0.0.0.0:{PORT}  (db: {DB_NAME})")
    print("Endpoints: POST /auth/signup, /auth/login, /history")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()

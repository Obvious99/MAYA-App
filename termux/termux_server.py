#!/usr/bin/env python3
"""
MAYA AI Assistant - Local Termux Python Companion Server
Runs on Android localhost at port 8082.
"""

import os
import sys
import json
import time
import base64
import urllib.request
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = 8082
HOST = "0.0.0.0"

FALLBACK_AVATAR_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "avatar.png")
AUDIO_OUTPUT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "response.mp3")

class MayaTermuxHandler(BaseHTTPRequestHandler):

    def _set_headers(self, status=200, content_type="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def do_OPTIONS(self):
        self._set_headers(204)

    def do_GET(self):
        url_path = urllib.parse.urlparse(self.path).path

        if url_path == "/avatar.png":
            if os.path.exists(FALLBACK_AVATAR_PATH):
                self._set_headers(200, "image/png")
                with open(FALLBACK_AVATAR_PATH, "rb") as f:
                    self.wfile.write(f.read())
            else:
                self._set_headers(404, "text/plain")
                self.wfile.write(b"Avatar file not found.")

        elif url_path == "/response.mp3":
            if os.path.exists(AUDIO_OUTPUT_PATH):
                self._set_headers(200, "audio/mpeg")
                with open(AUDIO_OUTPUT_PATH, "rb") as f:
                    self.wfile.write(f.read())
            else:
                self._set_headers(404, "text/plain")
                self.wfile.write(b"Audio file not generated yet.")

        elif url_path == "/status" or url_path == "/":
            self._set_headers(200, "application/json")
            payload = {
                "status": "online",
                "service": "MAYA Local Quantum Core",
                "version": "1.0.0",
                "endpoints": ["/chat", "/avatar.png", "/response.mp3"]
            }
            self.wfile.write(json.dumps(payload).encode("utf-8"))
        else:
            self._set_headers(404, "application/json")
            self.wfile.write(json.dumps({"error": "Endpoint not found"}).encode("utf-8"))

    def do_POST(self):
        url_path = urllib.parse.urlparse(self.path).path

        if url_path == "/chat":
            content_length = int(self.headers.get("Content-Length", 0))
            body_bytes = self.rfile.read(content_length)
            try:
                data = json.loads(body_bytes.decode("utf-8"))
            except Exception:
                data = {}

            user_message = data.get("message", "").strip()
            model_name = data.get("model", "gemini-2.5-flash")
            api_key = data.get("api_key") or os.environ.get("GEMINI_API_KEY", "")

            print(f"[MAYA Local Core] Received: '{user_message}' using {model_name}")

            reply_text = self.generate_response(user_message, model_name, api_key)
            self.generate_speech(reply_text)

            timestamp = int(time.time() * 1000)
            response_payload = {
                "text": reply_text,
                "audioUrl": f"/response.mp3?t={timestamp}"
            }

            self._set_headers(200, "application/json")
            self.wfile.write(json.dumps(response_payload).encode("utf-8"))
        else:
            self._set_headers(404, "application/json")
            self.wfile.write(json.dumps({"error": "Invalid POST endpoint"}).encode("utf-8"))

    def generate_response(self, message: str, model: str, api_key: str) -> str:
        if not message:
            return "Vocal sensors calibrated. How may I assist you?"

        if api_key:
            try:
                clean_model = model.replace("-latest", "")
                url = f"https://generativelanguage.googleapis.com/v1beta/models/{clean_model}:generateContent?key={api_key}"
                system_instruction = (
                    "You are MAYA, an elite holographic native Android AI assistant. "
                    "Provide concise, intelligent, futuristic, and helpful replies. Keep responses under 3 sentences for fluid speech."
                )
                payload = {
                    "contents": [{"parts": [{"text": f"System: {system_instruction}\nUser: {message}"}]}],
                    "generationConfig": {"temperature": 0.7, "maxOutputTokens": 300}
                }
                req = urllib.request.Request(
                    url,
                    data=json.dumps(payload).encode("utf-8"),
                    headers={"Content-Type": "application/json"},
                    method="POST"
                )
                with urllib.request.urlopen(req, timeout=8) as resp:
                    result = json.loads(resp.read().decode("utf-8"))
                    text = result["candidates"][0]["content"]["parts"][0]["text"].strip()
                    return text
            except Exception as e:
                print(f"[Gemini Call Error]: {e}")

        msg_lower = message.lower()
        if "who are you" in msg_lower or "name" in msg_lower:
            return "I am MAYA, your native holographic Android AI assistant. I run low-latency operations locally via your Termux quantum core."
        elif "time" in msg_lower or "date" in msg_lower:
            now_str = time.strftime("%A, %B %d, %I:%M %p")
            return f"Current local synchronized telemetry is {now_str}."
        elif "status" in msg_lower or "system" in msg_lower:
            return "All quantum neural pathways are green. Holographic projection pedestal is stable at 60 frames per second."
        elif "hello" in msg_lower or "hey" in msg_lower:
            return "Greetings. Voice interface linked and operational. What directive shall we execute today?"
        else:
            return f"Acknowledged: \"{message}\". Core matrices processed your query successfully."

    def generate_speech(self, text: str):
        try:
            from gtts import gTTS
            tts = gTTS(text=text, lang="en", tld="com")
            tts.save(AUDIO_OUTPUT_PATH)
        except Exception:
            pass

def run():
    server_address = (HOST, PORT)
    httpd = HTTPServer(server_address, MayaTermuxHandler)
    print("======================================================")
    print(f" [MAYA] Local Quantum Core Server running on port {PORT}")
    print(f" Chat Endpoint:   POST http://127.0.0.1:{PORT}/chat")
    print(f" Avatar Endpoint: GET  http://127.0.0.1:{PORT}/avatar.png")
    print(f" Audio Stream:    GET  http://127.0.0.1:{PORT}/response.mp3")
    print("======================================================")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        httpd.server_close()

if __name__ == "__main__":
    run()

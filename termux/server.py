#!/usr/bin/env python3
"""
MAYA Cyberpunk AI Assistant
Central Termux Brain + Gemini + Edge-TTS

Android client talks only to this server.
Gemini API key is kept in:
~/maya_agent/api_key.json

Server:
http://127.0.0.1:8082
"""

import os
import json
import time
import asyncio
from flask import Flask, request, jsonify, send_file
from flask_cors import CORS

from google import genai
import edge_tts


# ============================================================
# PATHS / CONFIG
# ============================================================

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
AUDIO_DIR = os.path.join(BASE_DIR, "static")
AUDIO_FILE = os.path.join(AUDIO_DIR, "response.mp3")
AVATAR_FILE = os.path.join(BASE_DIR, "avatar.png")

CONFIG_FILE = os.path.expanduser("~/maya_agent/api_key.json")

os.makedirs(AUDIO_DIR, exist_ok=True)


# ============================================================
# FLASK
# ============================================================

app = Flask(__name__)
CORS(app)


# ============================================================
# MAYA STATE
# ============================================================

chat_history = []

MAX_HISTORY = 12

DEFAULT_MODEL = "gemini-3.5-flash-lite"

VOICE_HINDI = "hi-IN-SwaraNeural"
VOICE_ENGLISH = "en-IN-NeerjaNeural"


# ============================================================
# CONFIGURATION
# ============================================================

def load_config():
    config = {
        "api_key": "",
        "model": DEFAULT_MODEL
    }

    try:
        if os.path.exists(CONFIG_FILE):
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)

            if isinstance(data, dict):
                config.update(data)

    except Exception as e:
        print(f"[!] Config error: {e}")

    return config


# ============================================================
# LANGUAGE DETECTION
# ============================================================

def contains_devanagari(text):
    return any("\u0900" <= ch <= "\u097F" for ch in text)


def select_voice(text):
    if contains_devanagari(text):
        return VOICE_HINDI

    return VOICE_ENGLISH


# ============================================================
# EDGE TTS
# ============================================================

async def generate_edge_tts(text, output_file):
    voice = select_voice(text)

    print(f"[*] TTS voice: {voice}")

    communicate = edge_tts.Communicate(
        text=text,
        voice=voice,
        rate="+8%"
    )

    await communicate.save(output_file)


def generate_speech(text):
    try:
        asyncio.run(generate_edge_tts(text, AUDIO_FILE))

        timestamp = int(time.time() * 1000)

        return f"/response.mp3?t={timestamp}"

    except Exception as e:
        print(f"[!] Edge-TTS error: {e}")
        return ""


# ============================================================
# GEMINI
# ============================================================

SYSTEM_INSTRUCTION = """
You are MAYA, a smart personal AI assistant running locally on an Android device.

Personality:
- Natural
- Intelligent
- Helpful
- Slightly futuristic/cyberpunk
- Never overly robotic
- Speak naturally like a real assistant

Language rules:
1. If the user speaks English, reply in natural English.
2. If the user speaks Hindi, reply in proper Hindi using Devanagari.
3. If the user speaks Hinglish, reply in natural Hindi using Devanagari.
4. Do not write Hindi/Hinglish using Roman letters.
5. Keep normal conversational answers concise, usually 1-3 sentences.
6. For explanations, lists, stories or detailed requests, provide the amount of detail required.

IMPORTANT:
You are the central conversational brain.
Do not claim an Android action was completed unless the action executor actually reports success.
"""


def generate_reply(user_message, config):
    api_key = str(config.get("api_key", "")).strip()
    model_name = str(
        config.get("model", DEFAULT_MODEL)
    ).strip() or DEFAULT_MODEL

    if not api_key:
        return (
            "मेरा Gemini neural link अभी configured नहीं है। "
            "कृपया Termux में API configuration जाँचें।"
        )

    try:
        client = genai.Client(api_key=api_key)

        contents = []

        for item in chat_history[-MAX_HISTORY:]:
            contents.append(
                f"{item['role']}: {item['text']}"
            )

        contents.append(f"user: {user_message}")

        response = client.models.generate_content(
            model=model_name,
            contents="\n".join(contents),
            config={
                "system_instruction": SYSTEM_INSTRUCTION
            }
        )

        reply = (response.text or "").strip()

        if not reply:
            return "I received your transmission, but the neural response was empty."

        return reply

    except Exception as e:
        print(f"[!] Gemini API error: {e}")

        return (
            "I’m connected to the MAYA brain, but the Gemini neural bridge "
            "returned an error. Please check the backend configuration."
        )


# ============================================================
# CHAT
# ============================================================

@app.route("/chat", methods=["POST"])
def chat():

    try:
        data = request.get_json(force=True) or {}

        user_message = str(
            data.get("message", "")
        ).strip()

        if not user_message:
            return jsonify({
                "text": "",
                "audioUrl": ""
            })

        print()
        print("=" * 60)
        print(f"[*] USER: {user_message}")

        config = load_config()

        print(
            f"[*] MODEL: "
            f"{config.get('model', DEFAULT_MODEL)}"
        )

        # ----------------------------------------------------
        # Generate response
        # ----------------------------------------------------

        reply_text = generate_reply(
            user_message,
            config
        )

        print(f"[*] MAYA: {reply_text}")

        # ----------------------------------------------------
        # Store conversation
        # ----------------------------------------------------

        chat_history.append({
            "role": "user",
            "text": user_message
        })

        chat_history.append({
            "role": "assistant",
            "text": reply_text
        })

        if len(chat_history) > MAX_HISTORY:
            del chat_history[:-MAX_HISTORY]

        # ----------------------------------------------------
        # Central voice pipeline
        # ----------------------------------------------------

        audio_url = generate_speech(reply_text)

        print(f"[*] AUDIO: {audio_url}")
        print("=" * 60)

        return jsonify({
            "text": reply_text,
            "audioUrl": audio_url
        })

    except Exception as e:

        print(f"[!] Server exception: {e}")

        return jsonify({
            "error": str(e),
            "text": "MAYA encountered a backend error.",
            "audioUrl": ""
        }), 500


# ============================================================
# HEALTH
# ============================================================

@app.route("/health", methods=["GET"])
def health():

    config = load_config()

    return jsonify({
        "status": "online",
        "service": "MAYA",
        "backend": "Termux Python",
        "gemini_configured": bool(
            str(config.get("api_key", "")).strip()
        ),
        "model": config.get(
            "model",
            DEFAULT_MODEL
        ),
        "tts": "Edge-TTS",
        "port": 8082
    })


# ============================================================
# AVATAR
# ============================================================

@app.route("/avatar.png", methods=["GET"])
def avatar():

    if os.path.exists(AVATAR_FILE):

        return send_file(
            AVATAR_FILE,
            mimetype="image/png"
        )

    return jsonify({
        "error": "MAYA avatar not found"
    }), 404


# ============================================================
# AUDIO
# ============================================================

@app.route("/response.mp3", methods=["GET"])
def audio():

    if os.path.exists(AUDIO_FILE):

        return send_file(
            AUDIO_FILE,
            mimetype="audio/mpeg"
        )

    return jsonify({
        "error": "Audio not available"
    }), 404


# ============================================================
# START SERVER
# ============================================================

if __name__ == "__main__":

    print()
    print("=" * 60)
    print("        MAYA CYBERPUNK AI ASSISTANT")
    print("=" * 60)
    print("Backend : Termux Python")
    print("URL     : http://127.0.0.1:8082")
    print("Chat    : http://127.0.0.1:8082/chat")
    print("Health  : http://127.0.0.1:8082/health")
    print("Avatar  : http://127.0.0.1:8082/avatar.png")
    print("Voice   : Edge-TTS")
    print("=" * 60)
    print()

    app.run(
        host="0.0.0.0",
        port=8082,
        debug=False
    )

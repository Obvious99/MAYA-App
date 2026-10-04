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
import base64
from datetime import datetime
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
MEMORY_FILE = os.path.join(BASE_DIR, "memory.json")

# Optional AI provider configuration.
# Gemini remains the primary MAYA brain.
OPENAI_API_KEY = os.environ.get("MAYA_OPENAI_API_KEY", "").strip()
OPENAI_MODEL = os.environ.get("MAYA_OPENAI_MODEL", "gpt-5").strip() or "gpt-5"

# Open image-generation model configuration.
# Uses an open-weight model through Hugging Face Inference Providers.
HF_TOKEN = os.environ.get("HF_TOKEN", "").strip()
IMAGE_MODEL = os.environ.get(
    "MAYA_IMAGE_MODEL",
    "black-forest-labs/FLUX.1-schnell"
).strip() or "black-forest-labs/FLUX.1-schnell"


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
# DATE / TIME CONTEXT
# ============================================================

def get_datetime_context():
    now = datetime.now().astimezone()

    return {
        "date": now.strftime("%d-%m-%Y"),
        "day": now.strftime("%A"),
        "time": now.strftime("%I:%M %p"),
        "timezone": now.tzname() or "local"
    }


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
# MAYA MEMORY
# ============================================================

def load_memory():
    default_memory = {
        "user_profile": {},
        "memories": [],
        "preferences": {}
    }

    try:
        if os.path.exists(MEMORY_FILE):
            with open(MEMORY_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)

            if isinstance(data, dict):
                for key, value in default_memory.items():
                    if key not in data:
                        data[key] = value
                return data
    except Exception as e:
        print(f"[!] Memory load error: {e}")

    return default_memory


def save_memory(memory):
    try:
        temp_file = MEMORY_FILE + ".tmp"
        with open(temp_file, "w", encoding="utf-8") as f:
            json.dump(memory, f, ensure_ascii=False, indent=2)

        os.replace(temp_file, MEMORY_FILE)
        return True
    except Exception as e:
        print(f"[!] Memory save error: {e}")
        return False


def get_memory_context():
    memory = load_memory()
    context = []

    if memory.get("user_profile"):
        context.append(f"User profile: {json.dumps(memory['user_profile'], ensure_ascii=False)}")

    if memory.get("preferences"):
        context.append(f"User preferences: {json.dumps(memory['preferences'], ensure_ascii=False)}")

    if memory.get("memories"):
        context.append(f"Stored memories: {json.dumps(memory['memories'][-20:], ensure_ascii=False)}")

    return "\n".join(context)


def remember_explicit_memory(user_message):
    text = user_message.strip()
    lower = text.lower()

    triggers = (
        "remember that ",
        "remember this ",
        "remember my ",
        "please remember ",
        "don't forget that ",
        "dont forget that "
    )

    matched = next((trigger for trigger in triggers if lower.startswith(trigger)), None)

    if not matched:
        return False

    memory_text = text[len(matched):].strip(" .!?")
    if not memory_text:
        return False

    memory = load_memory()

    if memory_text not in memory["memories"]:
        memory["memories"].append(memory_text)
        memory["memories"] = memory["memories"][-100:]
        saved = save_memory(memory)

        if saved:
            print(f"[*] MEMORY SAVED: {memory_text}")
            return True

    return False


# ============================================================
# LANGUAGE DETECTION
# ============================================================

def contains_devanagari(text):
    return any("\u0900" <= ch <= "\u097F" for ch in text)


HINGLISH_WORDS = {
    "mera", "meri", "mere", "mujhe", "mujhko", "hum", "humara", "hamara",
    "aap", "aapka", "aapki", "aapke", "tum", "tumhara", "tumhari",
    "kya", "kyu", "kyun", "kaise", "kaisa", "kaisi", "kab", "kahan",
    "hai", "hain", "tha", "thi", "the", "ho", "hoga", "hogi", "karna",
    "kar", "karo", "kiya", "chahiye", "nahi", "nahin", "haan", "achha",
    "accha", "bahut", "abhi", "phir", "sirf", "mujhse", "aapse",
    "batao", "bataiye", "samjhao", "samjhaiye", "madad", "pasand",
    "wala", "wali", "wale", "mein", "mera", "apna", "apni", "apne"
}


def is_hinglish(text):
    words = text.lower().replace("?", " ").replace("!", " ").replace(",", " ").split()
    if not words:
        return False

    matches = sum(1 for word in words if word in HINGLISH_WORDS)
    return matches >= 1 and matches / len(words) >= 0.15


def select_voice(text):
    if contains_devanagari(text) or is_hinglish(text):
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
1. ALWAYS determine the language of the user's LATEST message first.
2. If the latest user message is English, reply ENTIRELY in natural English.
3. If the latest user message is Hindi written in Devanagari, reply ENTIRELY in proper Hindi using Devanagari.
4. If the latest user message is clearly Hinglish, reply naturally in Hindi using Devanagari.
5. NEVER let previous conversation messages or stored memories change the language of the current reply.
6. Do not write Hindi/Hinglish using Roman letters.
7. Do not translate an English question into Hindi unless the user explicitly asks for translation.
8. Keep normal conversational answers concise, usually 1-3 sentences.
9. For explanations, lists, stories or detailed requests, provide the amount of detail required.

IMPORTANT:
You are the central conversational brain.
When "MAYA CURRENT DEVICE LOCATION (COARSE)" is provided, it is the device-reported coarse location available to you. Use those coordinates to answer location questions. Do not say that you cannot access GPS when this context is present. Do not invent a more precise location than the supplied coarse coordinates support.
Do not claim an Android action was completed unless the action executor actually reports success.

ACTION SYSTEM:
When the user's request requires an Android/device/app action, append exactly one internal action marker at the END of your response:

MAYA_ACTION: {"type":"ACTION_TYPE", ...}

The action marker must contain valid JSON on one line.

Supported action types:
SEARCH, YOUTUBE, WHATSAPP, REMINDER, CAMERA, LENS, SCREEN_READ, SCREEN_IDENTIFY, OCR, IMAGE_ANALYZE, IMAGE_GENERATE, PDF_CREATE, CHATGPT, GEMINI, STUDY_MODE, LOCATION, MAPS, CALL, SMS, MUSIC, APP_OPEN, URL_OPEN.

Rules:
- Emit an action marker ONLY when an actual action is needed.
- Do not emit an action marker for ordinary questions or explanations.
- Never claim an action succeeded. Android will execute it and report the actual result.
- Never invent phone numbers, contact details, URLs, or missing parameters.
- Require clear user intent for consequential actions such as calls or sending messages.
- For image generation, PDF creation, and study tasks, include the complete useful prompt/content in the action JSON.
- Keep the normal conversational reply natural and concise.
"""



def parse_maya_action(reply):
    marker = "MAYA_ACTION:"
    if marker not in reply:
        return reply.strip(), None

    before, action_text = reply.split(marker, 1)
    action_text = action_text.strip()

    try:
        action = json.loads(action_text)
        if not isinstance(action, dict):
            raise ValueError("Action JSON must be an object.")
        if not action.get("type"):
            raise ValueError("Action type is missing.")
        return before.strip(), action
    except Exception as e:
        print(f"[!] Invalid MAYA action: {e}")
        return reply.strip(), None

def generate_openai_reply(user_message, system_instruction=SYSTEM_INSTRUCTION):
    """Optional OpenAI provider using Python's standard HTTPS client."""
    if not OPENAI_API_KEY.strip():
        return None

    try:
        import urllib.request

        payload = {
            "model": OPENAI_MODEL,
            "instructions": system_instruction,
            "input": user_message
        }

        body = json.dumps(payload).encode("utf-8")

        req = urllib.request.Request(
            "https://api.openai.com/v1/responses",
            data=body,
            headers={
                "Authorization": f"Bearer {OPENAI_API_KEY.strip()}",
                "Content-Type": "application/json"
            },
            method="POST"
        )

        with urllib.request.urlopen(req, timeout=60) as response:
            response_data = json.loads(
                response.read().decode("utf-8")
            )

        text = response_data.get("output_text", "")

        if not text:
            for item in response_data.get("output", []):
                for content in item.get("content", []):
                    if content.get("type") == "output_text":
                        text += content.get("text", "")

        return text.strip() or None

    except Exception as e:
        print(f"[!] OpenAI API error: {e}")
        return None


def generate_reply(user_message, config, latitude=None, longitude=None, image_uri=None, image_base64=None):
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

        datetime_context = get_datetime_context()
        contents.append(
            "MAYA CURRENT DATE/TIME:\n"
            + json.dumps(datetime_context, ensure_ascii=False)
        )

        memory_context = get_memory_context()
        if memory_context:
            contents.append(f"MAYA LONG-TERM MEMORY:\n{memory_context}")

        if latitude is not None and longitude is not None:
            location_context = {
                "latitude": latitude,
                "longitude": longitude,
                "precision": "coarse"
            }
            contents.append(
                "MAYA CURRENT DEVICE LOCATION (COARSE):\n"
                + json.dumps(location_context, ensure_ascii=False)
            )

        for item in chat_history[-MAX_HISTORY:]:
            contents.append(
                f"{item['role']}: {item['text']}"
            )

        contents.append(f"user: {user_message}")

        prompt_text = "\n".join(contents)
        gemini_contents = [prompt_text]

        if image_base64:
            try:
                image_bytes = base64.b64decode(image_base64, validate=True)

                if image_bytes.startswith(b"\x89PNG"):
                    mime_type = "image/png"
                elif image_bytes.startswith(b"\xff\xd8\xff"):
                    mime_type = "image/jpeg"
                elif image_bytes.startswith(b"GIF8"):
                    mime_type = "image/gif"
                elif image_bytes.startswith(b"RIFF") and image_bytes[8:12] == b"WEBP":
                    mime_type = "image/webp"
                else:
                    mime_type = "image/jpeg"

                gemini_contents.append(
                    genai.types.Part.from_bytes(
                        data=image_bytes,
                        mime_type=mime_type
                    )
                )

                print(
                    f"[*] VISION: image attached "
                    f"({mime_type}, {len(image_bytes)} bytes)"
                )
            except Exception as e:
                print(f"[!] Image decode error: {e}")

        response = client.models.generate_content(
            model=model_name,
            contents=gemini_contents,
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
# CREATION ENGINE
# ============================================================

GENERATED_DIR = os.path.join(BASE_DIR, "generated")
os.makedirs(GENERATED_DIR, exist_ok=True)


def generate_image_open_model(prompt):
    """Generate an image with an open-weight model through Hugging Face."""
    if not HF_TOKEN:
        print("[!] Image generation unavailable: HF_TOKEN is not configured.")
        return None

    try:
        import requests

        endpoint = (
            "https://router.huggingface.co/hf-inference/models/"
            + IMAGE_MODEL
        )

        response = requests.post(
            endpoint,
            headers={
                "Authorization": f"Bearer {HF_TOKEN}",
                "Accept": "image/png",
                "Content-Type": "application/json"
            },
            json={
                "inputs": str(prompt or "").strip()
            },
            timeout=120
        )

        if response.status_code != 200:
            print(
                f"[!] Image generation failed: "
                f"HTTP {response.status_code}"
            )
            return None

        image_bytes = response.content

        if not image_bytes:
            print("[!] Image generation returned empty data.")
            return None

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"MAYA_IMG_{timestamp}.png"
        output_path = os.path.join(GENERATED_DIR, filename)

        with open(output_path, "wb") as f:
            f.write(image_bytes)

        print(f"[*] IMAGE CREATED: {output_path}")
        return output_path

    except Exception as e:
        print(f"[!] Image generation error: {e}")
        return None


def create_pdf(title, content):
    try:
        from reportlab.lib.pagesizes import A4
        from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
        from reportlab.lib.enums import TA_CENTER
        from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer
        from reportlab.lib.units import mm
        from reportlab.pdfbase import pdfmetrics
        from reportlab.pdfbase.ttfonts import TTFont
        from xml.sax.saxutils import escape

        safe_title = str(title or "MAYA Document").strip()
        safe_content = str(content or "").strip()

        if not safe_content:
            return None

        devanagari_font = "/system/fonts/NotoSansDevanagari-VF.ttf"
        font_name = "Helvetica"

        if os.path.isfile(devanagari_font):
            try:
                pdfmetrics.registerFont(
                    TTFont("MayaDevanagari", devanagari_font)
                )
                font_name = "MayaDevanagari"
            except Exception as e:
                print(f"[!] Devanagari font registration failed: {e}")

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"MAYA_{timestamp}.pdf"
        output_path = os.path.join(GENERATED_DIR, filename)

        doc = SimpleDocTemplate(
            output_path,
            pagesize=A4,
            rightMargin=18 * mm,
            leftMargin=18 * mm,
            topMargin=18 * mm,
            bottomMargin=18 * mm
        )

        styles = getSampleStyleSheet()

        title_style = ParagraphStyle(
            "MayaTitle",
            parent=styles["Title"],
            fontName=font_name,
            alignment=TA_CENTER,
            spaceAfter=14
        )

        body_style = ParagraphStyle(
            "MayaBody",
            parent=styles["BodyText"],
            fontName=font_name,
            leading=15,
            spaceAfter=8
        )

        story = [
            Paragraph(escape(safe_title), title_style),
            Spacer(1, 4 * mm)
        ]

        for line in safe_content.splitlines():
            line = line.strip()

            if line:
                story.append(
                    Paragraph(escape(line), body_style)
                )
            else:
                story.append(
                    Spacer(1, 4 * mm)
                )

        doc.build(story)

        print(f"[*] PDF CREATED: {output_path}")
        return output_path

    except Exception as e:
        print(f"[!] PDF creation error: {e}")
        return None

def chat():

    try:
        data = request.get_json(force=True) or {}

        user_message = str(
            data.get("message", "")
        ).strip()

        latitude = data.get("latitude")
        longitude = data.get("longitude")
        image_uri = str(data.get("image_uri", "")).strip()
        image_base64 = str(data.get("image_base64", "")).strip()

        try:
            latitude = float(latitude) if latitude is not None else None
            longitude = float(longitude) if longitude is not None else None
        except (TypeError, ValueError):
            latitude = None
            longitude = None

        if not user_message:
            return jsonify({
                "text": "",
                "audioUrl": ""
            })

        memory_saved = remember_explicit_memory(user_message)

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

        raw_reply = generate_reply(
            user_message,
            config,
            latitude=latitude,
            longitude=longitude,
            image_uri=image_uri,
            image_base64=image_base64
        )

        reply_text, maya_action = parse_maya_action(raw_reply)

        print(f"[*] MAYA: {reply_text}")

        if maya_action:
            print(f"[*] ACTION: {json.dumps(maya_action, ensure_ascii=False)}")

            if maya_action.get("type") == "PDF_CREATE":
                pdf_path = create_pdf(
                    maya_action.get("title"),
                    maya_action.get("content")
                )

                if pdf_path:
                    maya_action["result"] = "success"
                    maya_action["file"] = pdf_path
                else:
                    maya_action["result"] = "failed"

            elif maya_action.get("type") == "IMAGE_GENERATE":
                image_prompt = str(
                    maya_action.get("prompt")
                    or maya_action.get("content")
                    or user_message
                ).strip()

                image_path = generate_image_open_model(image_prompt)

                if image_path:
                    maya_action["result"] = "success"
                    maya_action["file"] = image_path
                else:
                    maya_action["result"] = "failed"

            elif maya_action.get("type") == "CHATGPT":
                chatgpt_prompt = str(
                    maya_action.get("prompt")
                    or maya_action.get("content")
                    or user_message
                ).strip()

                chatgpt_reply = generate_openai_reply(chatgpt_prompt)

                if chatgpt_reply:
                    maya_action["result"] = "success"
                    maya_action["response"] = chatgpt_reply
                else:
                    maya_action["result"] = "failed"

            elif maya_action.get("type") == "GEMINI":
                gemini_prompt = str(
                    maya_action.get("prompt")
                    or maya_action.get("content")
                    or user_message
                ).strip()

                try:
                    gemini_reply = generate_reply(
                        gemini_prompt,
                        config
                    )

                    if gemini_reply:
                        maya_action["result"] = "success"
                        maya_action["response"] = gemini_reply
                    else:
                        maya_action["result"] = "failed"

                except Exception as e:
                    print(f"[!] Gemini action error: {e}")
                    maya_action["result"] = "failed"


            elif maya_action.get("type") == "STUDY_MODE":
                study_prompt = str(
                    maya_action.get("prompt")
                    or maya_action.get("content")
                    or user_message
                ).strip()

                try:
                    study_reply = generate_reply(
                        study_prompt,
                        config
                    )

                    if study_reply:
                        maya_action["result"] = "success"
                        maya_action["response"] = study_reply
                    else:
                        maya_action["result"] = "failed"

                except Exception as e:
                    print(f"[!] Study mode error: {e}")
                    maya_action["result"] = "failed"
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
            "audioUrl": audio_url,
            "action": maya_action
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
# GENERATED FILES
# ============================================================

@app.route("/generated/<path:filename>", methods=["GET"])
def generated_file(filename):
    try:
        safe_name = os.path.basename(filename)
        file_path = os.path.join(GENERATED_DIR, safe_name)

        if os.path.isfile(file_path):
            return send_file(file_path)

        return jsonify({
            "error": "Generated file not found"
        }), 404

    except Exception as e:
        print(f"[!] Generated file error: {e}")
        return jsonify({
            "error": "Unable to open generated file"
        }), 500


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

#!/usr/bin/env python3
"""
MAYA Cyberpunk AI Assistant - Local Termux Python Server
Runs on Android inside Termux at: http://127.0.0.1:8082

Requirements in Termux:
  pkg update && pkg install python ffmpeg
  pip install flask flask-cors gtts requests google-genai
"""

import os
import time
from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
from gtts import gTTS

app = Flask(__name__)
CORS(app)  # Enable Cross-Origin requests for both Android client & Web testing

AUDIO_CACHE_DIR = os.path.join(os.path.dirname(__file__), "static")
os.makedirs(AUDIO_CACHE_DIR, exist_ok=True)
AVATAR_PATH = os.path.join(AUDIO_CACHE_DIR, "avatar.png")

# Create a default cyberpunk avatar placeholder if not already present
if not os.path.exists(AVATAR_PATH):
    import urllib.request
    try:
        # High quality cyberpunk cutout avatar
        fallback_url = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80"
        urllib.request.urlretrieve(fallback_url, AVATAR_PATH)
    except Exception as e:
        print(f"[!] Warning: Could not download avatar template: {e}")

@app.route("/avatar.png", methods=["GET"])
def get_avatar():
    """Serves the transparent PNG cutout avatar for MAYA"""
    if os.path.exists(AVATAR_PATH):
        return send_file(AVATAR_PATH, mimetype="image/png")
    return jsonify({"error": "Avatar image not found"}), 404

@app.route("/response.mp3", methods=["GET"])
def get_audio():
    """Serves the generated speech audio file"""
    audio_file = os.path.join(AUDIO_CACHE_DIR, "response.mp3")
    if os.path.exists(audio_file):
        return send_file(audio_file, mimetype="audio/mpeg")
    return jsonify({"error": "Audio file not found"}), 404

@app.route("/chat", methods=["POST"])
def chat():
    """
    Chat endpoint for MAYA Assistant
    Expected payload:
      {
        "message": "user text",
        "model": "gemini-3.5-flash-lite",
        "api_key": "optional_api_key"
      }
    """
    try:
        data = request.get_json(force=True) or {}
        user_message = data.get("message", "").strip()
        model_name = data.get("model", "gemini-3.5-flash-lite")
        api_key = data.get("api_key") or os.environ.get("GEMINI_API_KEY", "")

        print(f"[*] Received query: '{user_message}' | Model: {model_name}")

        if not user_message:
            return jsonify({"text": "Awaiting audio transmission...", "audioUrl": ""})

        # 1. Generate AI Response
        reply_text = ""
        if api_key:
            try:
                from google import genai
                client = genai.Client(api_key=api_key)
                system_instruction = (
                    "You are MAYA, a sentient high-tech cyberpunk artificial intelligence companion. "
                    "Your responses are concise, intelligent, stylized with cybernetic jargon, and direct. "
                    "Keep responses under 2-3 sentences for rapid audio synthesis."
                )
                response = client.models.generate_content(
                    model=model_name,
                    contents=user_message,
                    config={"system_instruction": system_instruction}
                )
                reply_text = response.text.strip()
            except Exception as e:
                print(f"[!] Gemini API Error: {e}")
                reply_text = f"Neural bridge fallback: I processed your query '{user_message}'. All subroutines operational."
        else:
            # Smart Offline Cyberpunk Persona Responses
            lower = user_message.lower()
            if "status" in lower or "system" in lower:
                reply_text = "All neural nodes operational. Core temperature 34 Kelvin. Memory cache synchronized."
            elif "who are you" in lower:
                reply_text = "I am MAYA, your personal cybernetic companion running natively on your hardware."
            elif "hello" in lower or "hi" in lower:
                reply_text = "Greetings, operative. Neural link established. How can I assist you today?"
            elif "time" in lower:
                reply_text = f"Local chronometer marks {time.strftime('%H:%M:%S')}. System synchronization locked."
            else:
                reply_text = f"Affirmative. Command '{user_message}' executed through local Termux gateway."

        # 2. Synthesize Audio via gTTS (or Piper/Edge-TTS)
        audio_file = os.path.join(AUDIO_CACHE_DIR, "response.mp3")
        try:
            tts = gTTS(text=reply_text, lang="en", tld="com")
            tts.save(audio_file)
            timestamp = int(time.time() * 1000)
            audio_url = f"/response.mp3?t={timestamp}"
        except Exception as e:
            print(f"[!] TTS synthesis warning: {e}")
            audio_url = ""

        return jsonify({
            "text": reply_text,
            "audioUrl": audio_url
        })

    except Exception as e:
        print(f"[!] Server exception: {e}")
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    print("""
    ==================================================
       __  __           __     __      
      |  \/  |   /\    \ \   / //\    
      | \  / |  /  \    \ \_/ //  \   
      | |\/| | / /\ \    \   // /\ \  
      | |  | |/ ____ \    | |/ ____ \ 
      |_|  |_/_/    \_\   |_/_/    \_\
      CYBERPUNK NEURAL ASSISTANT (TERMUX BACKEND)
    ==================================================
    Running on: http://127.0.0.1:8082
    Chat URL:   http://127.0.0.1:8082/chat
    Avatar URL: http://127.0.0.1:8082/avatar.png
    ==================================================
    """)
    app.run(host="0.0.0.0", port=8082, debug=False)

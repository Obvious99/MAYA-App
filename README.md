# MAYA - Cyberpunk Native Android AI Assistant

A complete, production-ready Android Studio project written in **Kotlin** & **Jetpack Compose** featuring:
- Dynamic Holographic Pedestal Canvas with glowing radial gradients (#00E5FF cyan and #FF007F pink)
- Draggable Floating System Overlay Orb (Foreground Service with WindowManager TYPE_APPLICATION_OVERLAY)
- Local Termux Python Bridge (OkHttp client streaming audio to http://127.0.0.1:8082)
- Gemini Neural Engine integration with live speech recognition & playback

## Quick Setup:
1. Open this project in **Android Studio Hedgehog / Iguana / Jellyfish or Ladybug**.
2. Sync Gradle files (JDK 17).
3. In Termux on Android, run the included backend:
   ```bash
   pkg update && pkg install python ffmpeg
   pip install flask flask-cors gtts requests google-genai
   python termux/server.py
   ```
4. Run the app on your Android device or emulator.

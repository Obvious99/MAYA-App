# MAYA - Native Android AI Assistant
Production-grade native Android client and local Termux companion core.

## Setup Instructions:
1. Open this root directory in Android Studio (Giraffe / Hedgehog / Iguana / Koala / Ladybug).
2. Sync Gradle files.
3. Build & Run on an Android device or emulator with minSdk 26+ (recommended Android 10+).
4. Run the Termux companion server on Android or localhost:
   ```bash
   python termux/termux_server.py
   ```
5. In MAYA App Settings, confirm the Chat endpoint is:
   `http://127.0.0.1:8082/chat`

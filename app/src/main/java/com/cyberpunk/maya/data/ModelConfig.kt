package com.cyberpunk.maya.data

enum class GeminiModel(val modelId: String, val displayName: String) {
    FLASH_LITE("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite"),
    FLASH_3_6("gemini-3.6-flash", "Gemini 3.6 Flash");

    companion object {
        val DEFAULT = FLASH_LITE
    }
}

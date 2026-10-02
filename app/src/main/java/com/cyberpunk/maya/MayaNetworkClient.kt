package com.cyberpunk.maya

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Asynchronous OkHttp network client communicating with the Termux Python backend
 * and managing streaming MediaPlayer playback with state synchronization callbacks.
 */
class MayaNetworkClient(private val context: Context) {

    companion object {
        private const val TAG = "MayaNetworkClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<PlaybackListener>()

    interface PlaybackListener {
        fun onSpeakingStarted()
        fun onSpeakingFinished()
        fun onError(error: String)
    }

    interface NetworkCallback {
        fun onSuccess(replyText: String, audioPath: String)
        fun onFailure(error: String)
    }

    fun registerPlaybackListener(listener: PlaybackListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterPlaybackListener(listener: PlaybackListener) {
        listeners.remove(listener)
    }

    /**
     * Sends user message payload to Termux Python server:
     * POST "http://127.0.0.1:8082/chat"
     * Payload: {"message": "user text", "model": "selected_model", "api_key": "optional_key"}
     */
    fun sendChatMessage(
        serverUrl: String,
        message: String,
        model: String,
        apiKey: String,
        callback: NetworkCallback
    ) {
        val payload = JSONObject().apply {
            put("message", message)
            put("model", model)
            if (apiKey.isNotBlank()) {
                put("api_key", apiKey)
            }
        }

        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network request failed: ${e.message}", e)
                mainHandler.post {
                    callback.onFailure("Connection failed to Termux: ${e.localizedMessage ?: "Timeout"}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        val code = res.code
                        val errorBody = res.body?.string() ?: ""
                        Log.e(TAG, "Server error code $code: $errorBody")
                        mainHandler.post {
                            callback.onFailure("Server returned error: HTTP $code")
                        }
                        return
                    }

                    val responseString = res.body?.string() ?: ""
                    try {
                        val json = JSONObject(responseString)
                        val text = json.optString("text", "Transmission received.")
                        val audioUrl = json.optString("audioUrl", "")

                        mainHandler.post {
                            callback.onSuccess(text, audioUrl)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON parse exception: ${e.message}", e)
                        mainHandler.post {
                            callback.onFailure("Invalid response payload from Termux")
                        }
                    }
                }
            }
        })
    }

    /**
     * Streams and plays audio stream from Termux server endpoint.
     */
    fun playAudio(fullAudioUrl: String) {
        stopAudio()

        if (fullAudioUrl.isBlank()) {
            // No audio provided, notify finish immediately
            listeners.forEach { it.onSpeakingFinished() }
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setDataSource(fullAudioUrl)
                setOnPreparedListener { mp ->
                    mp.start()
                    mainHandler.post {
                        listeners.forEach { it.onSpeakingStarted() }
                    }
                }
                setOnCompletionListener {
                    mainHandler.post {
                        listeners.forEach { it.onSpeakingFinished() }
                    }
                    releaseMediaPlayer()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    mainHandler.post {
                        listeners.forEach { it.onError("Audio playback error ($what, $extra)") }
                    }
                    releaseMediaPlayer()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio stream: ${e.message}", e)
            listeners.forEach { it.onError("Audio initialization failed: ${e.message}") }
        }
    }

    fun stopAudio() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping media player: ${e.message}")
        } finally {
            releaseMediaPlayer()
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing media player: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    fun release() {
        stopAudio()
        listeners.clear()
    }
}

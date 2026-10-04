package com.maya.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

interface MayaAudioPlaybackListener {
    fun onAudioPlaybackStarted()
    fun onAudioPlaybackCompleted()
    fun onAudioPlaybackError(error: String)
}

class MayaNetworkClient(
    private val context: Context,
    private val playbackListener: MayaAudioPlaybackListener
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun sendChatRequest(
        url: String,
        message: String,
        model: String,
        apiKey: String,
        latitude: Double? = null,
        longitude: Double? = null,
        imageUri: String? = null,
        imageBase64: String? = null,
        onSuccess: (text: String, audioUrl: String, action: JSONObject?) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        val payloadJson = JSONObject().apply {
            put("message", message)
            put("model", model)
            if (latitude != null && longitude != null) {
                put("latitude", latitude)
                put("longitude", longitude)
            }
            if (!imageBase64.isNullOrBlank()) {
                put("image_base64", imageBase64)
            } else if (!imageUri.isNullOrBlank()) {
                put("image_uri", imageUri)
            }
            if (apiKey.isNotBlank()) {
                put("api_key", apiKey)
            }
        }

        val requestBody = payloadJson.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    onError("Failed to connect to local Termux server ($url): " + (e.localizedMessage ?: "Connection refused"))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        mainHandler.post {
                            onError("Server returned error code: " + resp.code + " - " + resp.message)
                        }
                        return
                    }

                    val responseBodyString = resp.body?.string()
                    if (responseBodyString.isNullOrBlank()) {
                        mainHandler.post {
                            onError("Empty response received from Termux server.")
                        }
                        return
                    }

                    try {
                        val json = JSONObject(responseBodyString)
                        val text = json.optString("text", "No textual reply received.")
                        val audioUrl = json.optString("audioUrl", "")
                        val actionJson =
                            if (json.has("action") && !json.isNull("action")) {
                                json.optJSONObject("action")
                            } else {
                                null
                            }

                        mainHandler.post {
                            onSuccess(text, audioUrl, actionJson)
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            onError("Failed to parse JSON response: " + e.localizedMessage)
                        }
                    }
                }
            }
        })
    }

    fun playAudioStream(fullAudioUrl: String) {
        stopAudio()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .build()
                )

                setDataSource(fullAudioUrl)

                setOnPreparedListener { mp ->
                    mp.start()
                    mainHandler.post {
                        playbackListener.onAudioPlaybackStarted()
                    }
                }

                setOnCompletionListener {
                    stopAudio()
                    mainHandler.post {
                        playbackListener.onAudioPlaybackCompleted()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    stopAudio()
                    mainHandler.post {
                        playbackListener.onAudioPlaybackError("Audio playback error (what: $what, extra: $extra)")
                    }
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            stopAudio()
            mainHandler.post {
                playbackListener.onAudioPlaybackError("MediaPlayer initialization failed: " + e.localizedMessage)
            }
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (ignored: Exception) {
        } finally {
            mediaPlayer = null
        }
    }

    fun release() {
        stopAudio()
        okHttpClient.dispatcher.cancelAll()
    }
}

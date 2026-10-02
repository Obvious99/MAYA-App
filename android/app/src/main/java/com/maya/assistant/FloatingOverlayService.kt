package com.maya.assistant

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Locale
import kotlin.math.abs

/**
 * Foreground service that displays a persistent, draggable floating neon orb
 * allowing global vocal interactions with MAYA over any Android application.
 */
class FloatingOverlayService : Service(), MayaAudioPlaybackListener {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var orbImageView: ImageView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private lateinit var networkClient: MayaNetworkClient
    private lateinit var prefs: MayaPreferences

    private var isListening = false
    private var isProcessing = false
    private var isSpeaking = false

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "maya_overlay_service_channel"
        private const val NOTIFICATION_ID = 4040
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        prefs = MayaPreferences(this)
        networkClient = MayaNetworkClient(this, this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        initializeSpeechRecognizer()
        createFloatingOrbView()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "MAYA Holographic Overlay Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Provides continuous access to MAYA AI assistant"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("MAYA Overlay Active")
            .setContentText("Draggable quantum orb is floating over screen")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    updateOrbVisual(OrbMode.LISTENING)
                }

                override fun onBeginningOfSpeech() {
                    isListening = true
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    isProcessing = true
                    updateOrbVisual(OrbMode.PROCESSING)
                }

                override fun onError(error: Int) {
                    isListening = false
                    isProcessing = false
                    updateOrbVisual(OrbMode.ERROR)
                    mainHandler.postDelayed({
                        updateOrbVisual(OrbMode.IDLE)
                    }, 2000)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull() ?: ""

                    if (spokenText.isNotBlank()) {
                        isProcessing = true
                        updateOrbVisual(OrbMode.PROCESSING)
                        executeNetworkQuery(spokenText)
                    } else {
                        isProcessing = false
                        updateOrbVisual(OrbMode.IDLE)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun executeNetworkQuery(query: String) {
        val serverUrl = prefs.getServerUrl()
        val model = prefs.getGeminiModel()
        val apiKey = prefs.getApiKey()

        networkClient.sendChatRequest(
            url = serverUrl,
            message = query,
            model = model,
            apiKey = apiKey,
            onSuccess = { _, audioUrl ->
                mainHandler.post {
                    isProcessing = false
                    if (audioUrl.isNotBlank()) {
                        val baseUrl = extractBaseUrl(serverUrl)
                        networkClient.playAudioStream("$baseUrl$audioUrl")
                    } else {
                        updateOrbVisual(OrbMode.IDLE)
                    }
                }
            },
            onError = { errorMessage ->
                mainHandler.post {
                    isProcessing = false
                    updateOrbVisual(OrbMode.ERROR)
                    Toast.makeText(this, "MAYA Overlay: $errorMessage", Toast.LENGTH_SHORT).show()
                    mainHandler.postDelayed({
                        updateOrbVisual(OrbMode.IDLE)
                    }, 2500)
                }
            }
        )
    }

    private fun extractBaseUrl(fullUrl: String): String {
        return try {
            val uri = android.net.Uri.parse(fullUrl)
            "${uri.scheme}://${uri.host}:${uri.port}"
        } catch (e: Exception) {
            "http://127.0.0.1:8082"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingOrbView() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val orbSize = (64 * resources.displayMetrics.density).toInt()

        layoutParams = WindowManager.LayoutParams(
            orbSize,
            orbSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 300
        }

        val imageView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val padding = (14 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        this.orbImageView = imageView
        this.floatingView = imageView

        updateOrbVisual(OrbMode.IDLE)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchStartTime = 0L

        imageView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    layoutParams.x = initialX + deltaX
                    layoutParams.y = initialY + deltaY
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)
                    val clickDuration = System.currentTimeMillis() - touchStartTime

                    if (deltaX < 15 && deltaY < 15 && clickDuration < 300) {
                        onOrbTapped()
                    }
                    true
                }

                else -> false
            }
        }

        windowManager.addView(floatingView, layoutParams)
    }

    private fun onOrbTapped() {
        if (isSpeaking) {
            networkClient.stopAudio()
            isSpeaking = false
            updateOrbVisual(OrbMode.IDLE)
            return
        }

        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            isProcessing = true
            updateOrbVisual(OrbMode.PROCESSING)
            return
        }

        try {
            speechRecognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            initializeSpeechRecognizer()
            speechRecognizer?.startListening(speechIntent)
        }
    }

    private enum class OrbMode {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING,
        ERROR
    }

    private fun updateOrbVisual(mode: OrbMode) {
        val view = orbImageView ?: return

        val (strokeColor, fillColor) = when (mode) {
            OrbMode.IDLE -> Pair(Color.parseColor("#00E5FF"), Color.parseColor("#E60B1120"))
            OrbMode.LISTENING -> Pair(Color.parseColor("#00FF9D"), Color.parseColor("#E6064E3B"))
            OrbMode.PROCESSING -> Pair(Color.parseColor("#9D00FF"), Color.parseColor("#E64A0E4E"))
            OrbMode.SPEAKING -> Pair(Color.parseColor("#FF007F"), Color.parseColor("#E6831843"))
            OrbMode.ERROR -> Pair(Color.parseColor("#FF1744"), Color.parseColor("#E67F1D1D"))
        }

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            setStroke((3 * resources.displayMetrics.density).toInt(), strokeColor)
        }

        view.background = drawable
        view.setColorFilter(strokeColor)
    }

    override fun onAudioPlaybackStarted() {
        mainHandler.post {
            isSpeaking = true
            updateOrbVisual(OrbMode.SPEAKING)
        }
    }

    override fun onAudioPlaybackCompleted() {
        mainHandler.post {
            isSpeaking = false
            updateOrbVisual(OrbMode.IDLE)
        }
    }

    override fun onAudioPlaybackError(error: String) {
        mainHandler.post {
            isSpeaking = false
            updateOrbVisual(OrbMode.ERROR)
            mainHandler.postDelayed({
                updateOrbVisual(OrbMode.IDLE)
            }, 2000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            windowManager.removeView(floatingView)
            floatingView = null
        }
        speechRecognizer?.destroy()
        networkClient.release()
    }
}

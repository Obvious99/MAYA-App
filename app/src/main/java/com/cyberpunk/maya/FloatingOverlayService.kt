package com.cyberpunk.maya

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
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
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Locale
import kotlin.math.abs

/**
 * Foreground Service creating a draggable, floating Cyberpunk neon mic orb
 * on top of all Android applications using WindowManager TYPE_APPLICATION_OVERLAY.
 *
 * Single tap triggers background speech recognition and streaming to MayaNetworkClient,
 * playing audio without bringing the main app to foreground.
 */
class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private lateinit var networkClient: MayaNetworkClient
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isListening = false
    private var isSpeaking = false

    companion object {
        private const val CHANNEL_ID = "maya_overlay_service_channel"
        private const val NOTIFICATION_ID = 2082
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        networkClient = MayaNetworkClient(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startForegroundServiceNotification()
        createFloatingOverlay()
        initSpeechEngine()
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MAYA Floating System Neural Link",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground overlay service running MAYA background voice assistant"
                lightColor = AndroidColor.CYAN
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MAYA // Neural Overlay Active")
            .setContentText("Tap overlay orb to talk with MAYA anywhere.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingOverlay() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 300
        }

        // Programmatically Build Cyberpunk Orb Layout
        val frame = FrameLayout(this)
        val density = resources.displayMetrics.density
        val orbSizePx = (64 * density).toInt()

        val orbParams = FrameLayout.LayoutParams(orbSizePx, orbSizePx)
        frame.layoutParams = orbParams

        // Outer Glowing Cyberpunk Circular Background
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AndroidColor.parseColor("#E60A0F1D")) // 90% opacity deep cyber navy
            setStroke((2.5f * density).toInt(), AndroidColor.parseColor("#00E5FF"))
        }
        frame.background = drawable

        // Center Mic Icon
        val micIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(AndroidColor.parseColor("#00E5FF"))
            val iconPad = (16 * density).toInt()
            setPadding(iconPad, iconPad, iconPad, iconPad)
        }
        frame.addView(micIcon)

        // Drag Physics & Tap Detection Listener
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        frame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams!!.x
                    initialY = layoutParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) {
                        isDragging = true
                        layoutParams!!.x = initialX + deltaX
                        layoutParams!!.y = initialY + deltaY
                        windowManager.updateViewLayout(overlayView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Single Tap Detected -> Toggle Voice Recognition
                        handleOverlayTap(frame, micIcon)
                    }
                    true
                }
                else -> false
            }
        }

        overlayView = frame
        windowManager.addView(overlayView, layoutParams)
    }

    private fun handleOverlayTap(frame: FrameLayout, micIcon: ImageView) {
        if (isSpeaking) {
            networkClient.stopAudio()
            isSpeaking = false
            updateOrbVisuals(frame, micIcon, StateVisual.IDLE)
            return
        }

        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            updateOrbVisuals(frame, micIcon, StateVisual.PROCESSING)
        } else {
            startOverlayListening(frame, micIcon)
        }
    }

    private fun startOverlayListening(frame: FrameLayout, micIcon: ImageView) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        updateOrbVisuals(frame, micIcon, StateVisual.LISTENING)
        isListening = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun initSpeechEngine() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    overlayView?.let { view ->
                        val frame = view as? FrameLayout
                        val micIcon = frame?.getChildAt(0) as? ImageView
                        if (frame != null && micIcon != null) {
                            updateOrbVisuals(frame, micIcon, StateVisual.PROCESSING)
                        }
                    }
                }
                override fun onError(error: Int) {
                    isListening = false
                    mainHandler.post {
                        overlayView?.let { view ->
                            val frame = view as? FrameLayout
                            val micIcon = frame?.getChildAt(0) as? ImageView
                            if (frame != null && micIcon != null) {
                                updateOrbVisuals(frame, micIcon, StateVisual.ERROR)
                                mainHandler.postDelayed({
                                    updateOrbVisuals(frame, micIcon, StateVisual.IDLE)
                                }, 2000)
                            }
                        }
                    }
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""

                    if (text.isNotBlank()) {
                        processQuery(text)
                    } else {
                        resetToIdle()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        // Listen for playback callbacks
        networkClient.registerPlaybackListener(object : MayaNetworkClient.PlaybackListener {
            override fun onSpeakingStarted() {
                isSpeaking = true
                overlayView?.let { view ->
                    val frame = view as? FrameLayout
                    val micIcon = frame?.getChildAt(0) as? ImageView
                    if (frame != null && micIcon != null) {
                        updateOrbVisuals(frame, micIcon, StateVisual.SPEAKING)
                    }
                }
            }

            override fun onSpeakingFinished() {
                isSpeaking = false
                resetToIdle()
            }

            override fun onError(error: String) {
                isSpeaking = false
                resetToIdle()
            }
        })
    }

    private fun processQuery(queryText: String) {
        val prefs = getSharedPreferences("maya_prefs", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "http://127.0.0.1:8082/chat") ?: "http://127.0.0.1:8082/chat"
        val model = prefs.getString("gemini_model", "gemini-3.5-flash-lite") ?: "gemini-3.5-flash-lite"
        val apiKey = prefs.getString("api_key", "") ?: ""

        networkClient.sendChatMessage(
            serverUrl = serverUrl,
            message = queryText,
            model = model,
            apiKey = apiKey,
            callback = object : MayaNetworkClient.NetworkCallback {
                override fun onSuccess(replyText: String, audioPath: String) {
                    val fullAudioUrl = if (audioPath.startsWith("http")) {
                        audioPath
                    } else {
                        val base = serverUrl.substringBefore("/chat")
                        "$base$audioPath"
                    }
                    networkClient.playAudio(fullAudioUrl)
                }

                override fun onFailure(error: String) {
                    mainHandler.post {
                        Toast.makeText(this@FloatingOverlayService, "MAYA Error: $error", Toast.LENGTH_SHORT).show()
                        resetToIdle()
                    }
                }
            }
        )
    }

    private enum class StateVisual {
        IDLE, LISTENING, PROCESSING, SPEAKING, ERROR
    }

    private fun updateOrbVisuals(frame: FrameLayout, micIcon: ImageView, state: StateVisual) {
        val density = resources.displayMetrics.density
        val strokeWidth = (2.5f * density).toInt()

        val (colorHex, pulse) = when (state) {
            StateVisual.IDLE -> Pair("#00E5FF", false)
            StateVisual.LISTENING -> Pair("#FF007F", true)
            StateVisual.PROCESSING -> Pair("#FFD600", true)
            StateVisual.SPEAKING -> Pair("#00E5FF", true)
            StateVisual.ERROR -> Pair("#FF1744", false)
        }

        val color = AndroidColor.parseColor(colorHex)

        val bg = frame.background as? GradientDrawable ?: GradientDrawable()
        bg.setStroke(strokeWidth, color)
        micIcon.setColorFilter(color)

        frame.clearAnimation()
        if (pulse) {
            val anim = AlphaAnimation(0.5f, 1.0f).apply {
                duration = 450
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            frame.startAnimation(anim)
        }
    }

    private fun resetToIdle() {
        mainHandler.post {
            overlayView?.let { view ->
                val frame = view as? FrameLayout
                val micIcon = frame?.getChildAt(0) as? ImageView
                if (frame != null && micIcon != null) {
                    updateOrbVisuals(frame, micIcon, StateVisual.IDLE)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        networkClient.release()
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
    }
}

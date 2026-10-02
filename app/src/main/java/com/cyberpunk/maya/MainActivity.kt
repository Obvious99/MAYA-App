package com.cyberpunk.maya

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * State Machine defining MAYA's operational lifecycle.
 */
enum class MayaState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

class MainActivity : ComponentActivity() {

    private lateinit var networkClient: MayaNetworkClient
    private var speechRecognizer: SpeechRecognizer? = null

    // Permissions Request Launcher
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Microphone permission is required for voice commands.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Network and Audio Playback Client
        networkClient = MayaNetworkClient(this)

        // Validate Audio Permission at Startup
        checkAudioPermission()

        setContent {
            MaterialTheme {
                MayaApp(
                    networkClient = networkClient,
                    onRequestOverlayPermission = { checkOverlayPermission() },
                    onRequestAudioPermission = { checkAudioPermission() }
                )
            }
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        networkClient.release()
    }
}

@Composable
fun MayaApp(
    networkClient: MayaNetworkClient,
    onRequestOverlayPermission: () -> Unit,
    onRequestAudioPermission: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("maya_prefs", Context.MODE_PRIVATE) }

    var currentState by remember { mutableStateOf(MayaState.IDLE) }
    var userTranscript by remember { mutableStateOf("") }
    var mayaResponseText by remember { mutableStateOf("Systems online. Ready for command.") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // Dynamic Preferences
    var serverUrl by remember {
        mutableStateOf(prefs.getString("server_url", "http://127.0.0.1:8082/chat") ?: "http://127.0.0.1:8082/chat")
    }
    var avatarUrl by remember {
        mutableStateOf(prefs.getString("avatar_url", "http://127.0.0.1:8082/avatar.png") ?: "http://127.0.0.1:8082/avatar.png")
    }
    var geminiModel by remember {
        mutableStateOf(prefs.getString("gemini_model", "gemini-3.5-flash-lite") ?: "gemini-3.5-flash-lite")
    }
    var apiKey by remember {
        mutableStateOf(prefs.getString("api_key", "") ?: "")
    }

    // Audio Playback Listeners connected to state machine
    DisposableEffect(networkClient) {
        val listener = object : MayaNetworkClient.PlaybackListener {
            override fun onSpeakingStarted() {
                currentState = MayaState.SPEAKING
            }

            override fun onSpeakingFinished() {
                if (currentState == MayaState.SPEAKING) {
                    currentState = MayaState.IDLE
                }
            }

            override fun onError(error: String) {
                currentState = MayaState.ERROR
                errorMessage = error
            }
        }
        networkClient.registerPlaybackListener(listener)
        onDispose {
            networkClient.unregisterPlaybackListener(listener)
        }
    }

    // Native Speech Recognizer Instance
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                currentState = MayaState.LISTENING
            }
            override fun onBeginningOfSpeech() {
                currentState = MayaState.LISTENING
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                currentState = MayaState.PROCESSING
            }
            override fun onError(error: Int) {
                currentState = MayaState.ERROR
                errorMessage = "Recognition error: code $error"
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull() ?: ""
                if (recognizedText.isNotBlank()) {
                    userTranscript = recognizedText
                    currentState = MayaState.PROCESSING

                    // Submit to Maya Network Client
                    networkClient.sendChatMessage(
                        serverUrl = serverUrl,
                        message = recognizedText,
                        model = geminiModel,
                        apiKey = apiKey,
                        callback = object : MayaNetworkClient.NetworkCallback {
                            override fun onSuccess(replyText: String, audioPath: String) {
                                mayaResponseText = replyText
                                errorMessage = null
                                // Play response audio stream
                                val fullAudioUrl = if (audioPath.startsWith("http")) {
                                    audioPath
                                } else {
                                    val base = serverUrl.substringBefore("/chat")
                                    "$base$audioPath"
                                }
                                networkClient.playAudio(fullAudioUrl)
                            }

                            override fun onFailure(error: String) {
                                currentState = MayaState.ERROR
                                errorMessage = error
                            }
                        }
                    )
                } else {
                    currentState = MayaState.IDLE
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    userTranscript = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer?.setRecognitionListener(recognitionListener)

        onDispose {
            speechRecognizer?.destroy()
        }
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onRequestAudioPermission()
            return
        }

        if (speechRecognizer == null) {
            Toast.makeText(context, "Speech recognition not available on device", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer.startListening(intent)
        currentState = MayaState.LISTENING
        userTranscript = ""
        errorMessage = null
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        if (currentState == MayaState.LISTENING) {
            currentState = MayaState.PROCESSING
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        // Holographic Cyberpunk Background Grid
        CyberpunkGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Action Bar
            TopCyberpunkBar(
                currentState = currentState,
                onSettingsClicked = { showSettings = true }
            )

            // Dynamic Holographic Avatar Stage with Canvas Pedestal
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HolographicAvatarStage(
                    avatarUrl = avatarUrl,
                    state = currentState,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Real-Time Frosted Glass Transcript & Response Card
            ResponseInterfaceCard(
                userTranscript = userTranscript,
                responseText = mayaResponseText,
                state = currentState,
                errorMessage = errorMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // Bottom Control Dock
            BottomVoiceControlDock(
                state = currentState,
                onMicClick = {
                    if (currentState == MayaState.LISTENING) {
                        stopListening()
                    } else {
                        startListening()
                    }
                },
                onCancelClick = {
                    networkClient.stopAudio()
                    speechRecognizer?.cancel()
                    currentState = MayaState.IDLE
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Settings Screen Overlay
        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200))
        ) {
            SettingsScreen(
                onDismiss = {
                    showSettings = false
                    // Reload prefs
                    serverUrl = prefs.getString("server_url", "http://127.0.0.1:8082/chat") ?: "http://127.0.0.1:8082/chat"
                    avatarUrl = prefs.getString("avatar_url", "http://127.0.0.1:8082/avatar.png") ?: "http://127.0.0.1:8082/avatar.png"
                    geminiModel = prefs.getString("gemini_model", "gemini-3.5-flash-lite") ?: "gemini-3.5-flash-lite"
                    apiKey = prefs.getString("api_key", "") ?: ""
                },
                onRequestOverlayPermission = onRequestOverlayPermission
            )
        }
    }
}

/**
 * Top Status Bar featuring Cyberpunk aesthetic, State Chip, and Settings Trigger.
 */
@Composable
fun TopCyberpunkBar(
    currentState: MayaState,
    onSettingsClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "MAYA // V2.0",
                color = Color(0xFF00E5FF),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "CYBERNETIC NEURAL LINK",
                color = Color(0xFF6B7280),
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Live Status Indicator Chip
            StateChip(state = currentState)

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(
                onClick = onSettingsClicked,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFF00E5FF)
                )
            }
        }
    }
}

@Composable
fun StateChip(state: MayaState) {
    val (color, label) = when (state) {
        MayaState.IDLE -> Pair(Color(0xFF00E5FF), "IDLE")
        MayaState.LISTENING -> Pair(Color(0xFFFF007F), "LISTENING")
        MayaState.PROCESSING -> Pair(Color(0xFFFFD600), "PROCESSING")
        MayaState.SPEAKING -> Pair(Color(0xFF00E5FF), "TRANSMITTING")
        MayaState.ERROR -> Pair(Color(0xFFFF1744), "FAULT")
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = if (state != MayaState.IDLE) alphaPulse else 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Holographic Avatar Stage:
 * Renders transparent cutout PNG with Coil AsyncImage and multi-layered glowing Canvas pedestal beneath.
 */
@Composable
fun HolographicAvatarStage(
    avatarUrl: String,
    state: MayaState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hologram_pedestal")

    // Sine wave pulse for speaking state
    val pulseSine by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sine"
    )

    // Animated glow factor based on state
    val targetGlow = when (state) {
        MayaState.IDLE -> 0.35f
        MayaState.LISTENING -> 0.85f
        MayaState.PROCESSING -> 0.65f
        MayaState.SPEAKING -> 0.70f + 0.30f * sin(pulseSine)
        MayaState.ERROR -> 0.90f
    }

    val glowIntensity by animateFloatAsState(
        targetValue = targetGlow,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "glowIntensity"
    )

    val scaleExpand by animateFloatAsState(
        targetValue = if (state == MayaState.LISTENING) 1.15f else if (state == MayaState.SPEAKING) 1.05f + 0.05f * sin(pulseSine) else 1.0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "pedestalScale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Compose Canvas Holographic Pedestal at her feet
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f
            val pedestalY = canvasHeight * 0.82f // Positioning below avatar feet

            val baseRadiusX = (canvasWidth * 0.42f) * scaleExpand
            val baseRadiusY = (canvasHeight * 0.08f) * scaleExpand

            val cyanNeon = Color(0xFF00E5FF)
            val pinkNeon = Color(0xFFFF007F)

            // Outer Multi-layer Radial Glow (Pedestal base)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyanNeon.copy(alpha = 0.55f * glowIntensity),
                        pinkNeon.copy(alpha = 0.25f * glowIntensity),
                        Color.Transparent
                    ),
                    center = Offset(centerX, pedestalY),
                    radius = baseRadiusX * 1.3f
                ),
                topLeft = Offset(centerX - baseRadiusX * 1.3f, pedestalY - baseRadiusY * 1.5f),
                size = Size(baseRadiusX * 2.6f, baseRadiusY * 3.0f)
            )

            // Mid Hologram Ring
            drawOval(
                color = cyanNeon.copy(alpha = 0.8f * glowIntensity),
                topLeft = Offset(centerX - baseRadiusX, pedestalY - baseRadiusY),
                size = Size(baseRadiusX * 2, baseRadiusY * 2),
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 15f), phase = pulseSine * 20f)
                )
            )

            // Inner Core Concentric Disc
            val innerRadiusX = baseRadiusX * 0.6f
            val innerRadiusY = baseRadiusY * 0.6f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        pinkNeon.copy(alpha = 0.8f * glowIntensity),
                        cyanNeon.copy(alpha = 0.3f * glowIntensity),
                        Color.Transparent
                    ),
                    center = Offset(centerX, pedestalY),
                    radius = innerRadiusX
                ),
                topLeft = Offset(centerX - innerRadiusX, pedestalY - innerRadiusY),
                size = Size(innerRadiusX * 2, innerRadiusY * 2)
            )

            // Vertical Hologram Projector Light Beams
            for (i in -3..3) {
                val beamX = centerX + (i * (baseRadiusX / 3.8f))
                val beamHeight = canvasHeight * 0.55f
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (i % 2 == 0) cyanNeon.copy(alpha = 0.15f * glowIntensity) else pinkNeon.copy(alpha = 0.12f * glowIntensity),
                            cyanNeon.copy(alpha = 0.35f * glowIntensity)
                        ),
                        startY = pedestalY - beamHeight,
                        endY = pedestalY
                    ),
                    start = Offset(beamX, pedestalY - beamHeight),
                    end = Offset(beamX, pedestalY),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Full Figure Cutout Avatar Image
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarUrl)
                .crossfade(true)
                .build(),
            contentDescription = "MAYA Hologram Avatar",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize(0.85f)
                .padding(bottom = 40.dp)
        )
    }
}

/**
 * Frosted-glass styled card displaying real-time transcripts and MAYA's replies.
 */
@Composable
fun ResponseInterfaceCard(
    userTranscript: String,
    responseText: String,
    state: MayaState,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val borderColor = when (state) {
        MayaState.ERROR -> Color(0xFFFF1744)
        MayaState.LISTENING -> Color(0xFFFF007F)
        MayaState.PROCESSING -> Color(0xFFFFD600)
        else -> Color(0xFF00E5FF)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0F1D).copy(alpha = 0.85f))
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // User Speech Transcript
            if (userTranscript.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "YOU > ",
                        color = Color(0xFFFF007F),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = userTranscript,
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Error display if present
            if (errorMessage != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color(0xFFFF1744),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // MAYA Assistant Reply
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "MAYA > ",
                    color = Color(0xFF00E5FF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = responseText,
                    color = Color(0xFFF1F5F9),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Bottom Voice Dock featuring the main Cyberpunk push-to-talk button.
 */
@Composable
fun BottomVoiceControlDock(
    state: MayaState,
    onMicClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dock_pulse")
    val buttonScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (state == MayaState.LISTENING) 1.12f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "btnScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cancel / Reset Button
        if (state != MayaState.IDLE) {
            IconButton(
                onClick = onCancelClick,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF64748B), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset State",
                    tint = Color(0xFF94A3B8)
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
        }

        // Main Cyberpunk Mic Orb Button
        val buttonGlow = when (state) {
            MayaState.LISTENING -> Color(0xFFFF007F)
            MayaState.PROCESSING -> Color(0xFFFFD600)
            MayaState.SPEAKING -> Color(0xFF00E5FF)
            MayaState.ERROR -> Color(0xFFFF1744)
            else -> Color(0xFF00E5FF)
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(buttonGlow.copy(alpha = 0.4f), Color(0xFF0F172A))
                    )
                )
                .border(2.dp, buttonGlow, CircleShape)
                .clickable { onMicClick() },
            contentAlignment = Alignment.Center
        ) {
            if (state == MayaState.PROCESSING) {
                CircularProgressIndicator(
                    color = Color(0xFFFFD600),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(
                    imageVector = if (state == MayaState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = buttonGlow,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

/**
 * Background Cyberpunk Grid drawing subtle futuristic perspective lines.
 */
@Composable
fun CyberpunkGridBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val gridColor = Color(0xFF00E5FF).copy(alpha = 0.04f)
        val step = 40.dp.toPx()

        for (x in 0..width.toInt() step step.toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(x.toFloat(), 0f),
                end = Offset(x.toFloat(), height),
                strokeWidth = 1f
            )
        }
        for (y in 0..height.toInt() step step.toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y.toFloat()),
                end = Offset(width, y.toFloat()),
                strokeWidth = 1f
            )
        }
    }
}

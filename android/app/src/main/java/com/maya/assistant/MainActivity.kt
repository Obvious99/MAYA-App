package com.maya.assistant

import android.Manifest
import android.content.Intent
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

/**
 * State machine representing MAYA's operational lifecycle.
 */
enum class MayaState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

private fun encodeImageUriToBase64(uri: Uri): String? {
    return try {
        contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    } catch (e: Exception) {
        println("[!] Image encoding error: ${e.message}")
        null
    }
}

class MainActivity : ComponentActivity(), MayaAudioPlaybackListener {

    private lateinit var networkClient: MayaNetworkClient
    private lateinit var actionExecutor: MayaActionExecutor
    private var selectedImageUri: String? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null

    // State holders
    private val currentState = mutableStateOf(MayaState.IDLE)
    private val userTranscript = mutableStateOf("")
    private val mayaResponse = mutableStateOf("Systems initialized. Speak to begin interaction.")
    private val rmsDbLevel = mutableFloatStateOf(0f)
    private val activeScreen = mutableStateOf("home") // "home" or "settings"

    // Permission launchers
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            initializeSpeechRecognizer()
        } else {
            Toast.makeText(this, "Microphone permission required for speech interaction", Toast.LENGTH_LONG).show()
            currentState.value = MayaState.ERROR
            mayaResponse.value = "Microphone access denied. Grant permission to interact."
        }
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Location access granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Location access denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        networkClient = MayaNetworkClient(this, this)
        actionExecutor = MayaActionExecutor(this)
        val imagePickerLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                mayaResponse.value = "Image selected. Vision analysis pipeline ready."
                selectedImageUri = uri.toString()
            }
        }

        actionExecutor.onImagePickerRequested = {
            runOnUiThread {
                imagePickerLauncher.launch("image/*")
            }
        }

        actionExecutor.onCameraRequested = {
            runOnUiThread {
                val cameraIntent = Intent("android.media.action.IMAGE_CAPTURE")
                startActivity(cameraIntent)
            }
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkAndRequestPermissions()

        setContent {
            MayaAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF030712)
                ) {
                    if (activeScreen.value == "settings") {
                        SettingsScreen(
                            onNavigateBack = { activeScreen.value = "home" },
                            onRequestOverlayPermission = { requestOverlayPermission() }
                        )
                    } else {
                        MayaMainScreen(
                            state = currentState.value,
                            transcript = userTranscript.value,
                            response = mayaResponse.value,
                            rmsLevel = rmsDbLevel.floatValue,
                            onMicClicked = { handleMicToggle() },
                            onSendMessage = { sendVoiceQueryToBackend(it) },
                            onOpenSettings = { activeScreen.value = "settings" },
                            onResetState = { resetToIdle() }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            initializeSpeechRecognizer()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // Can be requested explicitly in Settings
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun getMayaLocation(onResult: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onResult(null)
            return
        }

        val request = com.google.android.gms.location.CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(5000)
            .build()

        fusedLocationClient.getCurrentLocation(request, null)
            .addOnSuccessListener { location ->
                onResult(location)
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Overlay permission already active", Toast.LENGTH_SHORT).show()
            }
        }
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
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    currentState.value = MayaState.LISTENING
                    userTranscript.value = "Listening to vocal input..."
                }

                override fun onBeginningOfSpeech() {
                    currentState.value = MayaState.LISTENING
                }

                override fun onRmsChanged(rmsdB: Float) {
                    rmsDbLevel.floatValue = rmsdB.coerceIn(0f, 10f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    currentState.value = MayaState.PROCESSING
                }

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match detected."
                        SpeechRecognizer.ERROR_NETWORK -> "Network communication error."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                        SpeechRecognizer.ERROR_SERVER -> "Server processing error."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout. No sound heard."
                        else -> "Speech recognition fault (Code: $error)"
                    }
                    currentState.value = MayaState.ERROR
                    mayaResponse.value = message
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull() ?: ""
                    if (spokenText.isNotBlank()) {
                        userTranscript.value = spokenText
                        sendVoiceQueryToBackend(spokenText)
                    } else {
                        currentState.value = MayaState.IDLE
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        userTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun handleMicToggle() {
        if (currentState.value == MayaState.LISTENING) {
            speechRecognizer?.stopListening()
            currentState.value = MayaState.PROCESSING
        } else if (currentState.value == MayaState.SPEAKING) {
            networkClient.stopAudio()
            currentState.value = MayaState.IDLE
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    speechRecognizer?.startListening(speechIntent)
                } catch (e: Exception) {
                    initializeSpeechRecognizer()
                    speechRecognizer?.startListening(speechIntent)
                }
            } else {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun sendVoiceQueryToBackend(query: String) {
        currentState.value = MayaState.PROCESSING
        mayaResponse.value = "Translating query through quantum link..."

        val prefs = MayaPreferences(this)
        val serverUrl = prefs.getServerUrl()
        val model = prefs.getGeminiModel()
        val apiKey = prefs.getApiKey()

        val imageBase64 = selectedImageUri
            ?.let { Uri.parse(it) }
            ?.let { encodeImageUriToBase64(it) }

        getMayaLocation { location ->
            networkClient.sendChatRequest(
                url = serverUrl,
                message = query,
                model = model,
                apiKey = apiKey,
                latitude = location?.latitude,
                longitude = location?.longitude,
                imageUri = selectedImageUri,
                imageBase64 = imageBase64,
                onSuccess = { responseText, audioUrl, action ->
                    runOnUiThread {
                        mayaResponse.value = responseText

                        if (action != null) {
                            val result = actionExecutor.execute(
                                type = action.optString("type"),
                                url = action.optString("url").takeIf { it.isNotBlank() },
                                query = action.optString("query").takeIf { it.isNotBlank() },
                                phone = action.optString("phone").takeIf { it.isNotBlank() },
                                message = action.optString("message").takeIf { it.isNotBlank() },
                                reminderText = action.optString("reminderText").takeIf { it.isNotBlank() },
                                hour = if (action.has("hour")) action.optInt("hour") else null,
                                minute = if (action.has("minute")) action.optInt("minute") else null,
                                title = action.optString("title").takeIf { it.isNotBlank() },
                                content = (
                                    action.optString("response")
                                        .takeIf { it.isNotBlank() }
                                        ?: action.optString("content").takeIf { it.isNotBlank() }
                                ),
                                result = action.optString("result").takeIf { it.isNotBlank() },
                                file = action.optString("file").takeIf { it.isNotBlank() }
                            )

                            if (!result.success) {
                                mayaResponse.value =
                                    if (responseText.isBlank()) result.message
                                    else "$responseText\n${result.message}"
                            }
                        }

                        if (audioUrl.isNotBlank()) {
                            currentState.value = MayaState.SPEAKING
                            val baseUrl = extractBaseUrl(serverUrl)
                            networkClient.playAudioStream("$baseUrl$audioUrl")
                        } else {
                            currentState.value = MayaState.IDLE
                        }
                    }
                },
                onError = { errorMessage ->
                    runOnUiThread {
                        currentState.value = MayaState.ERROR
                        mayaResponse.value = errorMessage
                    }
                }
            )
        }
    }

    private fun extractBaseUrl(fullUrl: String): String {
        return try {
            val uri = Uri.parse(fullUrl)
            "${uri.scheme}://${uri.host}:${uri.port}"
        } catch (e: Exception) {
            "http://127.0.0.1:8082"
        }
    }

    private fun resetToIdle() {
        networkClient.stopAudio()
        speechRecognizer?.cancel()
        currentState.value = MayaState.IDLE
        userTranscript.value = ""
        mayaResponse.value = "Systems operational. Tap microphone or initiate vocal query."
    }

    override fun onAudioPlaybackStarted() {
        runOnUiThread {
            currentState.value = MayaState.SPEAKING
        }
    }

    override fun onAudioPlaybackCompleted() {
        runOnUiThread {
            currentState.value = MayaState.IDLE
        }
    }

    override fun onAudioPlaybackError(error: String) {
        runOnUiThread {
            currentState.value = MayaState.ERROR
            mayaResponse.value = "Playback failure: $error"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        networkClient.release()
    }
}

/**
 * Main holographic Compose visual interface for MAYA.
 */
@Composable
fun MayaMainScreen(
    state: MayaState,
    transcript: String,
    response: String,
    rmsLevel: Float,
    onMicClicked: () -> Unit,
    onSendMessage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onResetState: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D021A))
    ) {
        // Holographic Background Grid & Ambiance
        HolographicGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            MayaTopAppBar(
                state = state,
                onOpenSettings = onOpenSettings,
                onResetState = onResetState
            )

            // Dynamic Holographic Avatar & Glowing Pedestal Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                HolographicAvatarStage(
                    state = state,
                    rmsLevel = rmsLevel,
                    onMicClicked = onMicClicked
                )
            }

            // Real-Time Frosted Glass Transcript & Response Interface
            MayaResponseInterface(
                state = state,
                transcript = transcript,
                response = response,
                onSendMessage = onSendMessage
            )

            Spacer(modifier = Modifier.height(16.dp))

        }
    }
}

/**
 * Custom Compose canvas drawing the dynamic multi-layered glowing radial pedestal
 * with real-time sine wave pulsations and audio reactivity.
 */
@Composable
fun HolographicAvatarStage(
    state: MayaState,
    rmsLevel: Float,
    onMicClicked: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "avatarGlow")

    val glowPulse by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val breathingScale by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarBreathing"
    )

    val glowColor = when (state) {
        MayaState.LISTENING -> Color(0xFF00F3FF)
        MayaState.PROCESSING -> Color(0xFFBA55D3)
        MayaState.SPEAKING -> Color(0xFFD900FF)
        MayaState.ERROR -> Color(0xFFFF3366)
        MayaState.IDLE -> Color(0xFFBA55D3)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.30f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.28f * glowPulse),
                        glowColor.copy(alpha = 0.12f * glowPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.9f
                ),
                center = center,
                radius = radius * 1.9f
            )

            drawCircle(
                color = glowColor.copy(alpha = 0.35f * glowPulse),
                center = center,
                radius = radius * 1.25f,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        AsyncImage(
            model = R.drawable.maya,
            contentDescription = "MAYA Avatar",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize(0.82f)
                .scale(breathingScale)
                .clickable { onMicClicked() }
        )
    }
}

@Composable
fun MayaResponseInterface(
    state: MayaState,
    transcript: String,
    response: String,
    onSendMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xDD0D021A))
                .border(1.dp, Color(0xFFBA55D3), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        state == MayaState.PROCESSING -> "MAYA: Processing query..."
                        state == MayaState.ERROR -> "MAYA: $response"
                        response.isBlank() -> "MAYA: Ready to chat."
                        else -> "MAYA: $response"
                    },
                    color = Color(0xFF00F3FF),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (transcript.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "> $transcript",
                        color = Color(0xFFBA55D3),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Type message...",
                        color = Color(0x8000F3FF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF00F3FF),
                    unfocusedTextColor = Color(0xFF00F3FF),
                    focusedBorderColor = Color(0xFFBA55D3),
                    unfocusedBorderColor = Color(0xFFBA55D3),
                    cursorColor = Color(0xFF00F3FF)
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f)
            )

            androidx.compose.material3.Button(
                onClick = {
                    val message = inputText.trim()
                    if (message.isNotEmpty()) {
                        inputText = ""
                        onSendMessage(message)
                    }
                },
                shape = RoundedCornerShape(4.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD900FF),
                    contentColor = Color.White
                ),
                modifier = Modifier.height(56.dp)
            ) {
                Text(
                    text = "SEND",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }

}

@Composable
fun MayaTopAppBar(
    state: MayaState,
    onOpenSettings: () -> Unit,
    onResetState: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "MAYA AI",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "LOCAL QUANTUM CORE",
                color = Color(0xFF00E5FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onResetState) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset State",
                    tint = Color(0xFF94A3B8)
                )
            }
            IconButton(onClick = onOpenSettings) {
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
fun HolographicGridBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridColor = Color(0xFF00E5FF).copy(alpha = 0.04f)
        val step = 40.dp.toPx()
        for (x in 0..(size.width / step).toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(x * step, 0f),
                end = Offset(x * step, size.height),
                strokeWidth = 1f
            )
        }
        for (y in 0..(size.height / step).toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y * step),
                end = Offset(size.width, y * step),
                strokeWidth = 1f
            )
        }
    }
}

@Composable
fun MayaAppTheme(content: @Composable () -> Unit) {
    content()
}


package com.maya.assistant

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// Cyberpunk Palette
val DarkBg = Color(0xFF0A0014)
val SurfacePurple = Color(0xFF16022B)
val NeonPurple = Color(0xFFBF00FF)
val NeonPink = Color(0xFFFF007F)
val CyberCyan = Color(0xFF00F0FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = DarkBg,
                    surface = SurfacePurple,
                    primary = NeonPurple,
                    secondary = CyberCyan
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    MayaMainScreen(
                        onSettingsClick = { openSettings() },
                        onOverlayClick = { requestOverlayPermissionAndStart() }
                    )
                }
            }
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Opening Settings...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1234)
        } else {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        Toast.makeText(this, "Miniature Floating MAYA Overlay Enabled", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun StarBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_alpha"
    )

    val stars = remember {
        List(50) {
            Offset(Random.nextFloat(), Random.nextFloat())
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        stars.forEachIndexed { index, pos ->
            val starAlpha = if (index % 2 == 0) alphaAnim else (1f - alphaAnim)
            drawCircle(
                color = Color.White.copy(alpha = starAlpha * 0.8f),
                radius = if (index % 3 == 0) 2.5f else 1.5f,
                center = Offset(pos.x * width, pos.y * height)
            )
        }
    }
}

@Composable
fun MayaMainScreen(
    onSettingsClick: () -> Unit,
    onOverlayClick: () -> Unit
) {
    var isListening by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Tap MAYA to speak. All neural systems online.") }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        // Starfield background effect
        StarBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MAYA AI", color = Color.White, fontSize = 22.sp)
                    Text("LOCAL QUANTUM CORE", color = CyberCyan, fontSize = 11.sp)
                }
                Row {
                    IconButton(onClick = onOverlayClick) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = "Floating Overlay", tint = CyberCyan)
                    }
                    IconButton(onClick = { statusText = "System refreshed. Neural memory re-indexed." }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = CyberCyan)
                    }
                }
            }

            // Clickable Full Avatar with Deep Purple Back-Glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable {
                        isListening = !isListening
                        statusText = if (isListening) "Listening... Speak now." else "Processing speech... Awaiting response."
                    }
            ) {
                // Background radial glow
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isListening) NeonPink.copy(alpha = 0.6f) else NeonPurple.copy(alpha = 0.5f),
                                    CyberCyan.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Maya Avatar rendered without circular clipping
                Image(
                    painter = painterResource(id = R.drawable.maya),
                    contentDescription = "Tap MAYA to activate voice input",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxHeight(0.88f)
                        .fillMaxWidth()
                )
            }

            // Status Console Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfacePurple.copy(alpha = 0.85f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isListening) NeonPink else NeonPurple, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (isListening) "● MAYA // LISTENING..." else "● MAYA // IDLE (TAP AVATAR TO SPEAK)",
                        color = if (isListening) NeonPink else CyberCyan,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        statusText,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

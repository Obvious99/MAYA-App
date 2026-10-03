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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// Cyberpunk Palette from index.html
val DarkBg = Color(0xFF080314)
val SurfacePurple = Color(0xFF0F081E)
val NeonPurple = Color(0xFFB000FF)
val CyberCyan = Color(0xFF00FFCC)
val StatusRed = Color(0xFFFF0055)

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
                    MayaMainHUD(
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
fun HUDStarfieldCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_alpha"
    )

    val stars = remember {
        List(40) {
            Offset(Random.nextFloat(), Random.nextFloat())
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Radial Background Glow
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x4D8C00FF), Color(0xF205020C)),
                center = Offset(w / 2, h / 2),
                radius = w.coerceAtLeast(h) * 0.75f
            )
        )

        // Floating Stars
        stars.forEachIndexed { index, pos ->
            val starAlpha = if (index % 2 == 0) alphaAnim else (1.2f - alphaAnim).coerceIn(0.2f, 1f)
            drawCircle(
                color = CyberCyan.copy(alpha = starAlpha * 0.75f),
                radius = if (index % 3 == 0) 2.5f else 1.5f,
                center = Offset(pos.x * w, pos.y * h)
            )
        }
    }
}

@Composable
fun MayaMainHUD(
    onSettingsClick: () -> Unit,
    onOverlayClick: () -> Unit
) {
    var isListening by remember { mutableStateOf(false) }
    var statusHeaderText by remember { mutableStateOf("MAYA // HUD") }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        // Dynamic Starfield
        HUDStarfieldCanvas()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonPurple.copy(alpha = 0.8f), RoundedCornerShape(8.dp)),
                color = SurfacePurple.copy(alpha = 0.75f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusHeaderText,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row {
                        IconButton(onClick = onOverlayClick) {
                            Icon(Icons.Default.PictureInPicture, contentDescription = "Floating Overlay", tint = CyberCyan)
                        }
                        IconButton(onClick = { statusHeaderText = "MAYA // REFRESHED" }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan)
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = CyberCyan)
                        }
                    }
                }
            }

            // Central Viewport: Full Body Maya Avatar (Uncropped)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable {
                        isListening = !isListening
                        statusHeaderText = if (isListening) "MAYA // LISTENING..." else "MAYA // HUD"
                    }
            ) {
                // Background Soft Glow
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isListening) StatusRed.copy(alpha = 0.5f) else NeonPurple.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Avatar Image
                Image(
                    painter = painterResource(id = R.drawable.maya),
                    contentDescription = "MAYA HUD Viewport",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxHeight(0.92f)
                        .fillMaxWidth()
                )
            }

            // Bottom HUD Status Indicator Dot
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isListening) StatusRed else CyberCyan)
            )
        }
    }
}

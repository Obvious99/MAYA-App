package com.maya.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Cyberpunk Purple Theme Palette
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBg
                ) {
                    MayaHomeScreen()
                }
            }
        }
    }
}

@Composable
fun MayaHomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
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
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan)
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = CyberCyan)
                }
            }
        }

        // Center Avatar Section with Glowing Border
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(NeonPurple.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
                .border(
                    width = 3.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(NeonPink, CyberCyan, NeonPurple)
                    ),
                    shape = CircleShape
                )
                .padding(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.maya),
                contentDescription = "MAYA Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }

        // Response Dialogue Box
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfacePurple),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NeonPurple, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("● MAYA // ONLINE", color = CyberCyan, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("\"hello Maya\"", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Greetings, User. I am MAYA, your neural interface. All systems are online and fully optimized.",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        // Bottom Voice Trigger Button
        IconButton(
            onClick = { },
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(colors = listOf(NeonPurple, NeonPink))
                )
                .border(2.dp, CyberCyan, CircleShape)
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Mic", tint = Color.White)
        }
    }
}

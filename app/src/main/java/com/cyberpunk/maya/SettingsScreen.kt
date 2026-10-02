package com.cyberpunk.maya

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Settings Screen managing SharedPreferences persistence for:
 * - server_url (Default: "http://127.0.0.1:8082/chat")
 * - avatar_url (Default: "http://127.0.0.1:8082/avatar.png")
 * - gemini_model (Dropdown: "3.5 flash lite", "3.6 flash", "gemini-3.5-flash-lite")
 * - api_key (Encrypted/Hidden Text field)
 * - overlay_enabled (Toggle managing FloatingOverlayService)
 */
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("maya_prefs", Context.MODE_PRIVATE) }

    // State Variables bound to SharedPreferences
    var serverUrl by remember {
        mutableStateOf(prefs.getString("server_url", "http://127.0.0.1:8082/chat") ?: "http://127.0.0.1:8082/chat")
    }
    var avatarUrl by remember {
        mutableStateOf(prefs.getString("avatar_url", "http://127.0.0.1:8082/avatar.png") ?: "http://127.0.0.1:8082/avatar.png")
    }
    var selectedModel by remember {
        mutableStateOf(prefs.getString("gemini_model", "gemini-3.5-flash-lite") ?: "gemini-3.5-flash-lite")
    }
    var apiKey by remember {
        mutableStateOf(prefs.getString("api_key", "") ?: "")
    }
    var overlayEnabled by remember {
        mutableStateOf(prefs.getBoolean("overlay_enabled", false))
    }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val availableModels = listOf(
        "gemini-3.5-flash-lite",
        "3.5 flash lite",
        "3.6 flash"
    )

    fun savePreferences() {
        prefs.edit()
            .putString("server_url", serverUrl.trim())
            .putString("avatar_url", avatarUrl.trim())
            .putString("gemini_model", selectedModel)
            .putString("api_key", apiKey.trim())
            .putBoolean("overlay_enabled", overlayEnabled)
            .apply()

        // Handle Floating Overlay Service Lifecycle
        val serviceIntent = Intent(context, FloatingOverlayService::class.java)
        if (overlayEnabled) {
            if (Settings.canDrawOverlays(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                onRequestOverlayPermission()
                Toast.makeText(context, "Grant 'Display over other apps' permission to enable the overlay.", Toast.LENGTH_LONG).show()
                overlayEnabled = false
                prefs.edit().putBoolean("overlay_enabled", false).apply()
            }
        } else {
            context.stopService(serviceIntent)
        }

        Toast.makeText(context, "Configuration Synchronized", Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF00E5FF)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "MAYA // SYSTEM CONFIG",
                        color = Color(0xFF00E5FF),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "LOCAL TERMUX & CLOUD NEURAL BRIDGES",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 1: Network & Endpoints
            SettingsSectionHeader(title = "TERMUX BACKEND ENDPOINTS", icon = Icons.Default.Link)

            Spacer(modifier = Modifier.height(8.dp))

            CyberpunkTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = "Chat Endpoint URL",
                placeholder = "http://127.0.0.1:8082/chat",
                helperText = "Termux Python HTTP POST server endpoint"
            )

            Spacer(modifier = Modifier.height(14.dp))

            CyberpunkTextField(
                value = avatarUrl,
                onValueChange = { avatarUrl = it },
                label = "Avatar Image Cutout URL",
                placeholder = "http://127.0.0.1:8082/avatar.png",
                helperText = "Transparent PNG stream endpoint"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: AI Model Selection & Key
            SettingsSectionHeader(title = "GEMINI NEURAL ENGINE", icon = Icons.Default.SmartToy)

            Spacer(modifier = Modifier.height(8.dp))

            // Model Dropdown Selector
            Text(
                text = "MODEL ARCHITECTURE",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { dropdownExpanded = !dropdownExpanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedModel,
                        color = Color(0xFFF1F5F9),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Expand Models",
                        tint = Color(0xFF00E5FF)
                    )
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = model,
                                    color = if (model == selectedModel) Color(0xFFFF007F) else Color(0xFFF1F5F9),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (model == selectedModel) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                selectedModel = model
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Optional API Key Text Field
            Text(
                text = "GEMINI API KEY (OPTIONAL)",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "AIzaSy...",
                        color = Color(0xFF475569),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle API Key Visibility",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedTextColor = Color(0xFFF1F5F9),
                    unfocusedTextColor = Color(0xFFF1F5F9)
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Floating System Overlay Toggle
            SettingsSectionHeader(title = "SYSTEM FLOATING OVERLAY", icon = Icons.Default.Layers)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Floating Neon Mic Orb",
                        color = Color(0xFFF1F5F9),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Access MAYA over any Android application with a single tap.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                Switch(
                    checked = overlayEnabled,
                    onCheckedChange = { isChecked ->
                        overlayEnabled = isChecked
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.3f),
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Save Configuration Button
            Button(
                onClick = { savePreferences() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save",
                    tint = Color(0xFF050811)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SAVE & SYNCHRONIZE",
                    color = Color(0xFF050811),
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFFF007F),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = Color(0xFFFF007F),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun CyberpunkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    helperText: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    placeholder,
                    color = Color(0xFF475569),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00E5FF),
                unfocusedBorderColor = Color(0xFF334155),
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A),
                focusedTextColor = Color(0xFFF1F5F9),
                unfocusedTextColor = Color(0xFFF1F5F9)
            ),
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = helperText,
            color = Color(0xFF475569),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

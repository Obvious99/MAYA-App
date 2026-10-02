package com.maya.assistant

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SharedPreferences storage manager for MAYA configuration parameters.
 */
class MayaPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("maya_settings_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_AVATAR_URL = "avatar_url"
        const val KEY_GEMINI_MODEL = "gemini_model"
        const val KEY_API_KEY = "api_key"
        const val KEY_OVERLAY_ENABLED = "overlay_enabled"

        const val DEFAULT_SERVER_URL = "http://127.0.0.1:8082/chat"
        const val DEFAULT_AVATAR_URL = "http://127.0.0.1:8082/avatar.png"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }

    fun getServerUrl(): String = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    fun setServerUrl(url: String) = prefs.edit().putString(KEY_SERVER_URL, url).apply()

    fun getAvatarUrl(): String = prefs.getString(KEY_AVATAR_URL, DEFAULT_AVATAR_URL) ?: DEFAULT_AVATAR_URL
    fun setAvatarUrl(url: String) = prefs.edit().putString(KEY_AVATAR_URL, url).apply()

    fun getGeminiModel(): String = prefs.getString(KEY_GEMINI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    fun setGeminiModel(model: String) = prefs.edit().putString(KEY_GEMINI_MODEL, model).apply()

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""
    fun setApiKey(key: String) = prefs.edit().putString(KEY_API_KEY, key).apply()

    fun isOverlayEnabled(): Boolean = prefs.getBoolean(KEY_OVERLAY_ENABLED, false)
    fun setOverlayEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
}

/**
 * Production-ready Settings Screen interface for configuring backend endpoints,
 * model selection, Gemini credentials, and floating overlay service lifecycle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { MayaPreferences(context) }

    var serverUrl by remember { mutableStateOf(prefs.getServerUrl()) }
    var avatarUrl by remember { mutableStateOf(prefs.getAvatarUrl()) }
    var selectedModel by remember { mutableStateOf(prefs.getGeminiModel()) }
    var apiKey by remember { mutableStateOf(prefs.getApiKey()) }
    var overlayEnabled by remember { mutableStateOf(prefs.isOverlayEnabled()) }

    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var showApiKeyText by remember { mutableStateOf(false) }

    val modelOptions = listOf(
        "gemini-2.5-flash",
        "gemini-1.5-flash",
        "gemini-1.5-pro"
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .padding(20.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF00E5FF)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "MAYA CONFIGURATION",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "NETWORK & LOCAL RUNTIME ENVIRONMENT",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        SettingCard(title = "Local Termux Chat Endpoint") {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL", color = Color(0xFF94A3B8)) },
                placeholder = { Text(MayaPreferences.DEFAULT_SERVER_URL, color = Color(0xFF475569)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors()
            )
        }

        SettingCard(title = "Holographic Avatar PNG Endpoint") {
            OutlinedTextField(
                value = avatarUrl,
                onValueChange = { avatarUrl = it },
                label = { Text("Avatar URL", color = Color(0xFF94A3B8)) },
                placeholder = { Text(MayaPreferences.DEFAULT_AVATAR_URL, color = Color(0xFF475569)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors()
            )
        }

        SettingCard(title = "Gemini Model Selection") {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0B1120))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .clickable { isModelDropdownExpanded = true }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedModel,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Expand models",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                }

                DropdownMenu(
                    expanded = isModelDropdownExpanded,
                    onDismissRequest = { isModelDropdownExpanded = false },
                    modifier = Modifier.background(Color(0xFF0F172A))
                ) {
                    modelOptions.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = model,
                                    color = if (model == selectedModel) Color(0xFF00E5FF) else Color(0xFFE2E8F0),
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            onClick = {
                                selectedModel = model
                                isModelDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        SettingCard(title = "Gemini API Key (Optional)") {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key", color = Color(0xFF94A3B8)) },
                placeholder = { Text("Enter custom key if not defined in Termux", color = Color(0xFF475569)) },
                visualTransformation = if (showApiKeyText) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors(),
                trailingIcon = {
                    Text(
                        text = if (showApiKeyText) "HIDE" else "SHOW",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { showApiKeyText = !showApiKeyText }
                            .padding(8.dp)
                    )
                }
            )
        }

        SettingCard(title = "Background Floating Overlay") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Global Draggable Neon Orb",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Access MAYA over any Android application without opening main window",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = overlayEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                onRequestOverlayPermission()
                            } else {
                                overlayEnabled = true
                                prefs.setOverlayEnabled(true)
                                val intent = Intent(context, FloatingOverlayService::class.java)
                                ContextCompatStartService(context, intent)
                                Toast.makeText(context, "Overlay service started", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            overlayEnabled = false
                            prefs.setOverlayEnabled(false)
                            val intent = Intent(context, FloatingOverlayService::class.java)
                            context.stopService(intent)
                            Toast.makeText(context, "Overlay service stopped", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF0891B2),
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                prefs.setServerUrl(serverUrl)
                prefs.setAvatarUrl(avatarUrl)
                prefs.setGeminiModel(selectedModel)
                prefs.setApiKey(apiKey)
                prefs.setOverlayEnabled(overlayEnabled)

                Toast.makeText(context, "Configuration successfully saved", Toast.LENGTH_SHORT).show()
                onNavigateBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00E5FF),
                contentColor = Color.Black
            )
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = "Save", tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SAVE CONFIGURATION",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

private fun ContextCompatStartService(context: Context, intent: Intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

@Composable
fun SettingCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.85f))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF00E5FF),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun textFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color(0xFFE2E8F0),
    focusedContainerColor = Color(0xFF0B1120),
    unfocusedContainerColor = Color(0xFF0B1120),
    focusedIndicatorColor = Color(0xFF00E5FF),
    unfocusedIndicatorColor = Color(0xFF334155),
    cursorColor = Color(0xFF00E5FF)
)

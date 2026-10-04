package com.maya.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

class MayaActionExecutor(private val context: Context) {

    data class ActionResult(
        val success: Boolean,
        val message: String,
        val data: String? = null
    )

    var onCameraRequested: (() -> Unit)? = null
    var onImagePickerRequested: (() -> Unit)? = null

    fun execute(
        type: String,
        url: String? = null,
        query: String? = null,
        phone: String? = null,
        message: String? = null,
        reminderText: String? = null,
        hour: Int? = null,
        minute: Int? = null,
        title: String? = null,
        content: String? = null,
        result: String? = null,
        file: String? = null
    ): ActionResult {

        return try {
            when (type.uppercase()) {

                "SEARCH" -> {
                    val searchQuery = query?.trim()
                    if (searchQuery.isNullOrBlank()) {
                        ActionResult(false, "Search query is missing.")
                    } else {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "https://www.google.com/search?q=" +
                                    Uri.encode(searchQuery)
                            )
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        ActionResult(true, "Search opened.")
                    }
                }

                "YOUTUBE" -> {
                    val youtubeUrl = url?.trim()

                    if (youtubeUrl.isNullOrBlank()) {
                        ActionResult(false, "A direct YouTube video URL is required.")
                    } else {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(youtubeUrl)
                        ).apply {
                            setPackage("app.revanced.android.youtube")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        try {
                            context.startActivity(intent)
                            ActionResult(true, "ReVanced YouTube opened.")
                        } catch (e: Exception) {
                            ActionResult(
                                false,
                                "ReVanced YouTube could not open the video URL."
                            )
                        }
                    }
                }

                "WHATSAPP" -> {
                    if (phone.isNullOrBlank()) {
                        ActionResult(false, "WhatsApp phone number is missing.")
                    } else {
                        val cleanPhone = phone.filter { it.isDigit() || it == '+' }
                        val waUrl = buildString {
                            append("https://wa.me/")
                            append(cleanPhone.removePrefix("+"))
                            if (!message.isNullOrBlank()) {
                                append("?text=")
                                append(Uri.encode(message))
                            }
                        }

                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(waUrl)
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        ActionResult(true, "WhatsApp opened.")
                    }
                }

                "PDF_CREATE" -> {
                    if (!result.equals("success", ignoreCase = true) || file.isNullOrBlank()) {
                        ActionResult(
                            false,
                            "PDF creation failed."
                        )
                    } else {
                        try {
                            val fileName = file.substringAfterLast("/")
                            val pdfUrl = "http://127.0.0.1:8082/generated/" +
                                Uri.encode(fileName)

                            val cacheFile = File(context.cacheDir, fileName)

                            val connection =
                                URL(pdfUrl).openConnection() as HttpURLConnection

                            connection.connectTimeout = 10000
                            connection.readTimeout = 20000
                            connection.requestMethod = "GET"

                            if (connection.responseCode !in 200..299) {
                                val code = connection.responseCode
                                connection.disconnect()

                                ActionResult(
                                    false,
                                    "PDF download failed. HTTP $code"
                                )
                            } else {
                                connection.inputStream.use { input ->
                                    FileOutputStream(cacheFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }

                                connection.disconnect()

                                val pdfUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cacheFile
                                )

                                val intent = Intent(
                                    Intent.ACTION_VIEW
                                ).apply {
                                    setDataAndType(pdfUri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }

                                context.startActivity(intent)

                                ActionResult(
                                    true,
                                    "PDF created and opened.",
                                    pdfUri.toString()
                                )
                            }
                        } catch (e: Exception) {
                            println("[!] PDF open error: ${e.message}")

                            ActionResult(
                                false,
                                "PDF was created, but Android could not open it."
                            )
                        }
                    }
                }

                "CHATGPT", "GEMINI" -> {
                    if (result.equals("success", ignoreCase = true) && !content.isNullOrBlank()) {
                        ActionResult(
                            true,
                            content
                        )
                    } else {
                        ActionResult(
                            false,
                            "AI provider did not return a response."
                        )
                    }
                }

                "IMAGE_GENERATE" -> {
                    if (!result.equals("success", ignoreCase = true) || file.isNullOrBlank()) {
                        ActionResult(
                            false,
                            "Image generation failed."
                        )
                    } else {
                        try {
                            val fileName = file.substringAfterLast("/")
                            val imageUrl = "http://127.0.0.1:8082/generated/" +
                                Uri.encode(fileName)

                            val cacheFile = File(context.cacheDir, fileName)

                            val connection =
                                URL(imageUrl).openConnection() as HttpURLConnection

                            connection.connectTimeout = 10000
                            connection.readTimeout = 120000
                            connection.requestMethod = "GET"

                            if (connection.responseCode !in 200..299) {
                                val code = connection.responseCode
                                connection.disconnect()

                                ActionResult(
                                    false,
                                    "Generated image download failed. HTTP $code"
                                )
                            } else {
                                connection.inputStream.use { input ->
                                    FileOutputStream(cacheFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }

                                connection.disconnect()

                                val imageUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cacheFile
                                )

                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(imageUri, "image/png")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }

                                context.startActivity(intent)

                                ActionResult(
                                    true,
                                    "Image created and opened.",
                                    imageUri.toString()
                                )
                            }
                        } catch (e: Exception) {
                            println("[!] Image open error: ${e.message}")

                            ActionResult(
                                false,
                                "Image was created, but Android could not open it."
                            )
                        }
                    }
                }

                "STUDY_MODE" -> {
                    if (result.equals("success", ignoreCase = true) && !content.isNullOrBlank()) {
                        ActionResult(
                            true,
                            content
                        )
                    } else {
                        ActionResult(
                            false,
                            "Study mode did not return a response."
                        )
                    }
                }

                "SCREEN_READ", "SCREEN_IDENTIFY" -> {
                    ActionResult(
                        false,
                        "Screen capture pipeline is not connected yet."
                    )
                }

                "OCR", "IMAGE_ANALYZE" -> {
                    onImagePickerRequested?.invoke()
                    ActionResult(
                        true,
                        "Please select an image for vision analysis."
                    )
                }

                "CAMERA" -> {
                    onCameraRequested?.invoke()
                    ActionResult(true, "Camera requested.")
                }

                "LENS" -> {
                    val lensIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("googleapp://lens")
                    )
                    lensIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    try {
                        context.startActivity(lensIntent)
                        ActionResult(true, "Google Lens opened.")
                    } catch (e: Exception) {
                        val fallbackIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://lens.google.com/")
                        )
                        fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(fallbackIntent)
                        ActionResult(true, "Google Lens web interface opened.")
                    }
                }

                "MUSIC" -> {
                    val musicQuery = query?.trim()
                    if (musicQuery.isNullOrBlank()) {
                        ActionResult(false, "Music query is missing.")
                    } else {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "https://www.youtube.com/results?search_query=" +
                                    Uri.encode(musicQuery)
                            )
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        ActionResult(true, "Music search opened.")
                    }
                }

                "LOCATION" -> {
                    val locationUrl = if (!query.isNullOrBlank()) {
                        "https://www.google.com/maps/search/?api=1&query=" +
                            Uri.encode(query.trim())
                    } else {
                        "https://www.google.com/maps"
                    }

                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(locationUrl)
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)

                    ActionResult(true, "Maps opened.")
                }

                "MAPS" -> {
                    val mapsQuery = query?.trim()
                    if (mapsQuery.isNullOrBlank()) {
                        ActionResult(false, "Maps query is missing.")
                    } else {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "https://www.google.com/maps/search/?api=1&query=" +
                                    Uri.encode(mapsQuery)
                            )
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        ActionResult(true, "Google Maps opened.")
                    }
                }

                "CALL" -> {
                    val callPhone = phone?.trim()
                    if (callPhone.isNullOrBlank()) {
                        ActionResult(false, "Phone number is missing.")
                    } else {
                        val cleanPhone = callPhone.filter { it.isDigit() || it == '+' }
                        val intent = Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:$cleanPhone")
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        ActionResult(true, "Phone dialer opened.")
                    }
                }

                "SMS" -> {
                    val smsPhone = phone?.trim()
                    if (smsPhone.isNullOrBlank()) {
                        ActionResult(false, "SMS phone number is missing.")
                    } else {
                        val cleanPhone = smsPhone.filter { it.isDigit() || it == '+' }
                        val intent = Intent(
                            Intent.ACTION_SENDTO,
                            Uri.parse("smsto:$cleanPhone")
                        ).apply {
                            if (!message.isNullOrBlank()) {
                                putExtra("sms_body", message)
                            }
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        ActionResult(true, "SMS composer opened.")
                    }
                }

                "URL_OPEN" -> {
                    if (url.isNullOrBlank()) {
                        ActionResult(false, "URL is missing.")
                    } else {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        ActionResult(true, "URL opened.")
                    }
                }

                "APP_OPEN" -> {
                    val packageName = query?.trim()
                    if (packageName.isNullOrBlank()) {
                        ActionResult(false, "App package name is missing.")
                    } else {
                        val launchIntent = context.packageManager
                            .getLaunchIntentForPackage(packageName)

                        if (launchIntent == null) {
                            ActionResult(false, "App could not be found.")
                        } else {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            ActionResult(true, "App opened.")
                        }
                    }
                }

                "REMINDER" -> {
                    if (reminderText.isNullOrBlank() || hour == null || minute == null) {
                        ActionResult(false, "Reminder text or time is missing.")
                    } else {
                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)

                            if (timeInMillis <= System.currentTimeMillis()) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }

                        val alarmManager =
                            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                        val intent = Intent(
                            AlarmClock.ACTION_SET_ALARM
                        ).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, hour)
                            putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            putExtra(AlarmClock.EXTRA_MESSAGE, reminderText)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        context.startActivity(intent)

                        ActionResult(
                            true,
                            "Reminder request sent to the Android clock."
                        )
                    }
                }

                else -> {
                    ActionResult(false, "Unknown MAYA action: $type")
                }
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "MAYA action failed",
                Toast.LENGTH_SHORT
            ).show()

            ActionResult(
                false,
                "Action failed: ${e.message ?: "unknown error"}"
            )
        }
    }
}

package com.example.tony

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.tony.ui.theme.TonyTheme
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    private var heardText by mutableStateOf("Tap the microphone and say hello.")
    private var previousMediaVolume: Int = -1
    private var status by mutableStateOf("Tony is ready")
    private var permissionGranted = false

    private var permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            listen()
        } else {
            status = "Microphone permission is needed."
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tts = TextToSpeech(this, this)

//        setContent {
//            TonyTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
//                }
//            }
//        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TONY",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(status)
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = heardText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = { startListening() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Talk to TONY")
                        }
                    }
                }
            }
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            listen()
        }
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "No speech recognition service is available."
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status = "Listening..."
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                status = "Processing..."
            }

            override fun onError(error: Int) {
                status = "Couldn't recognize speech. Try again."
            }

            override fun onResults(results: Bundle?) {
                val result = results
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    ?.firstOrNull()

                if (result.isNullOrBlank()) {
                    status = "I didn't catch that. Try again."
                    return
                }

                heardText = result
                respond(result)
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to TONY")
        }
        status = "Starting microphone..."
        recognizer?.startListening(intent)
    }

    private fun respond(input: String) {
        val normalized = input.lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Handle time and date commands directly on the phone.
        val localAnswer = when {
            parseVolumeCommand(input) != null -> {
                parseVolumeCommand(input)!!
            }

            parseFlashlightCommand(input) != null -> {
                setFlashlight(parseFlashlightCommand(input)!!)
            }

            parseAppCommand(input) != null -> {
                val appName = parseAppCommand(input)!!
                launchApp(appName)
            }
            normalized.contains("timer") -> parseTimerCommand(input)

            normalized.contains("battery") || normalized.contains("charging") || normalized.contains("charge") -> {
                getBatteryStatus()
            }

            normalized.contains("time") &&
                    (
                            normalized.startsWith("what") ||
                                    normalized.startsWith("tell me") ||
                                    normalized.startsWith("current") ||
                                    normalized == "time" ||
                                    normalized == "time now"
                            ) -> {
                "The time is " +
                        SimpleDateFormat("h:mm a", Locale.getDefault())
                            .format(Date())
            }

            normalized.contains("date") ||
                    normalized == "what day is it" ||
                    normalized == "what is today" ||
                    normalized == "what day is today" -> {
                "Today is " +
                        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
                            .format(Date())
            }

            else -> null
        }

        if (localAnswer != null) {
            heardText = "You: $input\n\nTony: $localAnswer"
            status = "Tony is ready"

            tts?.speak(
                localAnswer,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "tony_local_reply"
            )
            return
        }

        status = "TONY is thinking"

        Thread {
            var connection: HttpURLConnection? = null

            try {
                val url = URL("http://192.168.121.109:8000/chat")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 10000
                connection.readTimeout = 60000
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                val requestBody = JSONObject()
                    .put("message", input)
                    .toString()

                connection.outputStream.use { output ->
                    output.write(requestBody.toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode

                if (responseCode !in 200..299) {
                    throw Exception("Server returned HTTP $responseCode")
                }

                val responseBody = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val answer = JSONObject(responseBody)
                    .getString("reply")

                runOnUiThread {
                    heardText = "You: $input\n\nTony: $answer"
                    status = "Tony is ready"

                    tts?.speak(
                        answer,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "tony_reply"
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    heardText = "You: $input\n\nSorry, I couldn't reach the AI service."
                    status = "Connection failed. Check your backend. error: $e"
                }
                android.util.Log.e("Tony", "AI request failed", e)
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun launchApp(appName: String): String {
        val apps = mapOf(
            "youtube" to listOf("com.google.android.youtube"),
            "whatsapp" to listOf("com.whatsapp"),
            "chrome" to listOf("com.android.chrome"),
            "camera" to listOf("com.nothing.camera"),
            "settings" to listOf("com.android.settings"),
            "phone" to listOf("com.google.android.dialer"),
            "messages" to listOf("com.google.android.apps.messaging"),
            "maps" to listOf("com.google.android.apps.maps"),
            "play store" to listOf("com.android.vending"),
            "clock" to listOf("com.google.android.deskclock"),
            "calendar" to listOf("com.google.android.calendar"),
            "gmail" to listOf("com.google.android.gm"),
            "files" to listOf("com.google.android.apps.nbu.files")
        )

        val packages = apps[appName]
            ?: return "$appName not configured by the developers yet."

        for (packageName in packages) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    return "Opening $appName..."
                }
            } catch (_: ActivityNotFoundException) {
                // TODO
            } catch (_: SecurityException) {
                // TODO
            }
        }
        return "Couldn't find $appName."
    }

    private fun parseAppCommand (input: String): String? {
        var command = input.lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Recognize several natural ways of asking to open an app.
        val prefixes = listOf(
            "could you please open ",
            "can you please open ",
            "could you open ",
            "can you open ",
            "please open ",
            "please launch ",
            "please start ",
            "open ",
            "launch ",
            "start ",
            "run ",
            "open up "
        )

        val prefix = prefixes.firstOrNull { command.startsWith(it) }
            ?: return null

        command = command.removePrefix(prefix).trim()

        // Remove common trailing words from voice-recognition results.
        command = command.removeSuffix(" please").trim()
        command = command.removeSuffix(" for me").trim()

        val supportedApps = listOf(
            "play store",
            "youtube",
            "whatsapp",
            "settings",
            "messages",
            "calendar",
            "camera",
            "chrome",
            "phone",
            "maps",
            "clock",
            "gmail",
            "files"
        )

        // Match the complete app name, not just a substring.
        return supportedApps.firstOrNull { command == it }
    }

    private fun parseTimerCommand (input: String): String? {
        val text = input.lowercase(Locale.getDefault())

        if (!text.contains("timer")) return null

        val hourMatch = Regex("""(\d+)\s*hours?""").find(text)
        val minuteMatch = Regex("""(\d+)\s*minutes?""").find(text)
        val secondMatch = Regex("""(\d+)\s*seconds?""").find(text)

        val hours = hourMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val minutes = minuteMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val seconds = secondMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        val totalSeconds = hours * 3600L + minutes * 60L + seconds

        if (totalSeconds <= 0 || totalSeconds > Int.MAX_VALUE) {
            return "Please specify a valid timer duration, such as 5 minutes."
        }

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds.toInt())
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Tony timer")
        }

        return try {
            if (intent.resolveActivity(packageManager) == null) {
                "I couldn't find a timer app."
            } else {
                startActivity(intent)
                "Opening the timer for ${formatDuration(totalSeconds)}"
            }
        } catch (_: Exception) {
            "I couldn't open the timer app."
        }
    }

    private fun parseFlashlightCommand(input: String): Boolean? {
        val text = input.lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val mentionsFlashlight =
            text.contains("flashlight") ||
            text.contains("flash light") ||
            text.contains("torch")

        if (!mentionsFlashlight) return null

        val turnOn = listOf(
            "turn on", "switch on", "enable", "activate", "turn the", "switch the", "start"
        ).any { text.contains(it) } &&
            listOf("on", "enable", "activate", "start").any {
                text.contains(it)
            }

        val turnOff = listOf(
            "turn off", "switch off", "disable", "deactivate", "turn the", "switch the", "stop"
        ).any { text.contains(it) } &&
            listOf("off", "disable", "deactivate", "stop").any {
                text.contains(it)
            }
        return when {
            turnOn && !turnOff -> true
            turnOff && !turnOn -> false
            else -> null
        }
    }

    private fun getBatteryStatus(): String {
        val batteryIntent = registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        if (batteryIntent == null) {
            return "Couldn't read battery status."
        }

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val statusValue = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        if (level < 0 || scale <= 0) {
            return "Couldn't determine battery level."
        }
        val percentage = (level * 100) / scale
        val charging = when (statusValue) {
            BatteryManager.BATTERY_STATUS_CHARGING ->
                "and charging."
            BatteryManager.BATTERY_STATUS_FULL ->
                "so fully charged."
            else -> "and not charging."
        }

        return "Battery level is $percentage percent $charging"
    }

    private fun parseVolumeCommand(input: String): String? {
        val text = input.lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N}\\s%]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val mentionsVolume =
            text.contains("volume") ||
            text.contains("sound") ||
            text.contains("louder") ||
            text.contains("quieter")

        if (!mentionsVolume) return null

        val percentMatch = Regex("""(\d{1,3})\s*%""")
            .find(text)
            ?: Regex("""(?:volume|sound)\s+(?:to\s+)?(\d{1,3})\b""")
                .find(text)

        val percent = percentMatch?.groupValues?.get(1)?.toIntOrNull()

        return when {
            listOf("unmute", "restore sound").any { text.contains(it) } -> {
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val restoreVolume = when {
                    previousMediaVolume > 0 -> previousMediaVolume
                    else -> (maxVolume * 0.5).toInt().coerceAtLeast(1)
                }
                previousMediaVolume = -1
                setMediaVolume(restoreVolume, false)
            }
            listOf("mute", "silence").any { text.contains(it) } -> {
                val audioManager =
                    getSystemService(AUDIO_SERVICE) as AudioManager
                val currentVolume =
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (currentVolume > 0) {
                    previousMediaVolume = currentVolume
                }
                setMediaVolume(0, false)
            }
            percent!= null && percent in 0..100 ->
                setMediaVolume(percent, true)
            listOf("increase", "raise", "up", "louder").any { text.contains(it) } -> {
                val audioManager =
                    getSystemService(AUDIO_SERVICE) as AudioManager
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = (current + 1).coerceAtMost(max)
                setMediaVolume(target, false)
            }
            listOf("decrease", "lower", "down", "quieter").any { text.contains(it) } -> {
                val audioManager =
                    getSystemService(AUDIO_SERVICE) as AudioManager
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val target = (current - 1).coerceAtLeast(0)
                setMediaVolume(target, false)
            }
            else -> null
        }
    }

    private fun setMediaVolume(target: Int, units: Boolean = true): String {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (units) {
            val targetVolume = (
                    maxVolume * target.coerceIn(0, 100) / 100.0
                    ).toInt()
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetVolume,
                0
            )
            return "Media volume set to $targetVolume percent."
        } else {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                target,
                0
            )
            return "Media volume set to $target."
        }
    }

    private fun setFlashlight(enabled: Boolean): String {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId == null) {
                return "Flashlight not found."
            }
            cameraManager.setTorchMode(cameraId, enabled)
            if (enabled) {
                "Flashlight turned on."
            } else {
                "Flashlight turned off."
            }
        } catch (_: SecurityException) {
            "No permissions for flashlight."
        } catch (_: Exception) {
            "Couldn't change flashlight."
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return buildList {
            if (hours > 0) add("$hours ${if (hours == 1L) "hour" else "hours"}")
            if (minutes > 0) add("$minutes ${if (minutes == 1L) "minute" else "minutes"}")
            if (seconds > 0) add("$seconds ${if (seconds == 1L) "second" else "seconds"}")
        }.joinToString(" and ")
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TonyTheme {
        Greeting("Android")
    }
}
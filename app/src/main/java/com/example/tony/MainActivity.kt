package com.example.tony

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    private var heardText by mutableStateOf("Tap the microphone and say hello.")
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
//        val answer = when {
//            input.contains("hello", ignoreCase = true) ->
//                "Hello! I'm Tony, your personal assistant."
//
//            input.contains("your name", ignoreCase = true) ->
//                "My name is Tony."
//
//            input.contains("time", ignoreCase = true) ->
//                "I can tell you the time after we add that feature."
//
//            else ->
//                "I heard you say: $input. Soon, I'll be able to answer using AI."
//        }
//
//        status = "Tony is responding..."
//        heardText = "You: $input\n\nTony: $answer"
//
//        tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "tony_reply")
//        status = "Tony is ready"
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
package com.jarvis.assistant

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var statusText: TextView
    private val httpClient = OkHttpClient()
    private val PREFS_NAME = "jarvis_prefs"
    private val KEY_API_KEY = "anthropic_api_key"

    // Saari permissions jo humein chahiye
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    )

    // Voice recognition result yahan aata hai
    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0) ?: ""
            handleCommand(spokenText)
        }
    }

    // Permission request result
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            speak("Sab permissions mil gayi. Ab bolo kya karna hai.")
        } else {
            statusText.text = "Kuch permissions nahi mili. App poora kaam nahi karega.\nSettings me ja kar manually allow karo."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        tts = TextToSpeech(this, this)

        val micButton = findViewById<FloatingActionButton>(R.id.micButton)
        micButton.setOnClickListener {
            if (hasAllPermissions()) {
                startListening()
            } else {
                permissionLauncher.launch(requiredPermissions)
            }
        }

        val settingsButton = findViewById<FloatingActionButton>(R.id.settingsButton)
        settingsButton.setOnClickListener { showApiKeyDialog() }

        if (getApiKey().isBlank()) {
            showApiKeyDialog()
        }
    }

    // ---- API key ko phone me hi save karna (SharedPreferences) ----
    private fun getApiKey(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    private fun saveApiKey(key: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    private fun showApiKeyDialog() {
        val input = EditText(this)
        input.hint = "Apna Anthropic API key yahan paste karo"
        input.setText(getApiKey())

        AlertDialog.Builder(this)
            .setTitle("Claude API Key")
            .setMessage("console.anthropic.com se apni API key banao aur yahan paste karo. Ye sirf tumhare phone me save hoti hai.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                saveApiKey(input.text.toString().trim())
                Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bolo...")
        }
        statusText.text = "Sun raha hoon..."
        speechLauncher.launch(intent)
    }

    private fun speak(text: String) {
        statusText.text = text
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // ---- Command samajhne aur execute karne ka core logic ----
    private fun handleCommand(command: String) {
        val lower = command.lowercase(Locale.getDefault()).trim()

        when {
            // Fast path: seedha "call <naam>" jaisa clear command
            lower.startsWith("call ") || lower.contains("ko call") -> {
                val name = extractName(lower, listOf("call ", "ko call karo", "ko call kro"))
                makeCall(name)
            }

            // Fast path: seedha "message <naam> <text>" jaisa clear command
            lower.startsWith("message ") || lower.contains("ko message") || lower.contains("send message") -> {
                val (name, msgBody) = extractMessageParts(lower)
                sendSms(name, msgBody)
            }

            // Baaki har tarah ke natural bolne ka style -> Claude AI se samjhwao
            // jaise: "yaar Rahul ko bata do main aa raha hoon" ya "mummy ko phone laga do"
            else -> {
                if (getApiKey().isBlank()) {
                    speak("Samajh nahi aaya, aur API key bhi set nahi hai. Settings me key daalo.")
                } else {
                    statusText.text = "Soch raha hoon..."
                    askClaude(command)
                }
            }
        }
    }

    // ---- Claude API se natural language command ko structured action me convert karwana ----
    private fun askClaude(userCommand: String) {
        val apiKey = getApiKey()
        val systemPrompt = """
            Tum ek phone assistant ke liye command parser ho. User Hindi/Hinglish me bolega.
            Tumhe sirf ek JSON object return karna hai, kuch aur nahi, koi extra text nahi.
            Format: {"action": "call" ya "message" ya "unknown", "name": "contact ka naam", "body": "message ka text agar action message hai warna khali string"}
            Example: "yaar rahul ko bata do main aa raha hoon" -> {"action":"message","name":"rahul","body":"main aa raha hoon"}
            Example: "mummy ko phone laga do" -> {"action":"call","name":"mummy","body":""}
            Agar samajh na aaye to action "unknown" do.
        """.trimIndent()

        val bodyJson = JSONObject().apply {
            put("model", "claude-sonnet-5")
            put("max_tokens", 300)
            put("system", systemPrompt)
            put("messages", org.json.JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", userCommand)
                }
            ))
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = bodyJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { speak("Internet ya API me error aaya.") }
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                val responseText = response.body?.string() ?: ""
                runOnUiThread {
                    try {
                        val outer = JSONObject(responseText)
                        if (outer.has("error")) {
                            val errMsg = outer.getJSONObject("error").optString("message", "Unknown error")
                            speak("API error: $errMsg")
                            return@runOnUiThread
                        }
                        val contentArray = outer.getJSONArray("content")
                        val rawText = contentArray.getJSONObject(0).getString("text").trim()
                        // Kabhi kabhi model ```json fences bhi de sakta hai, unko hata dete hain
                        val cleaned = rawText.replace("```json", "").replace("```", "").trim()
                        val parsed = JSONObject(cleaned)

                        val action = parsed.optString("action", "unknown")
                        val name = parsed.optString("name", "")
                        val msgBody = parsed.optString("body", "")

                        when (action) {
                            "call" -> makeCall(name)
                            "message" -> sendSms(name, msgBody)
                            else -> speak("Samajh nahi aaya ki kya karna hai.")
                        }
                    } catch (e: Exception) {
                        speak("Response samajhne me error aaya.")
                    }
                }
            }
        })
    }

    private fun extractName(text: String, triggers: List<String>): String {
        var result = text
        for (t in triggers) {
            result = result.replace(t, "")
        }
        return result.trim()
    }

    // "message rahul batao main aa raha hoon" -> name=rahul, body="batao main aa raha hoon"
    private fun extractMessageParts(text: String): Pair<String, String> {
        var cleaned = text
            .replace("send message to", "")
            .replace("message", "")
            .replace("ko message karo", "")
            .replace("ko message", "")
            .trim()

        val parts = cleaned.split(" ", limit = 2)
        val name = parts.getOrElse(0) { "" }
        val body = parts.getOrElse(1) { "" }
        return Pair(name.trim(), body.trim())
    }

    private fun findNumberByName(name: String): String? {
        if (name.isBlank()) return null
        val resolver = contentResolver
        val cursor: Cursor? = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return it.getString(numberIndex)
            }
        }
        return null
    }

    private fun makeCall(name: String) {
        val number = findNumberByName(name)
        if (number == null) {
            speak("$name naam ka contact nahi mila.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            speak("Call permission nahi hai.")
            return
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        startActivity(intent)
        speak("$name ko call laga raha hoon.")
    }

    private fun sendSms(name: String, body: String) {
        val number = findNumberByName(name)
        if (number == null) {
            speak("$name naam ka contact nahi mila.")
            return
        }
        if (body.isBlank()) {
            speak("Message me kya likhna hai wo bolo.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            speak("SMS permission nahi hai.")
            return
        }
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, body, null, null)
            speak("$name ko message bhej diya: $body")
        } catch (e: Exception) {
            speak("Message bhejne me error aaya.")
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

package com.qtmini.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var input: EditText
    private lateinit var mem: Memory
    private lateinit var actions: Actions
    private lateinit var ai: AiClient
    private lateinit var prefs: android.content.SharedPreferences
    private val msgs = mutableListOf<Pair<String, String>>()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sr: SpeechRecognizer? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        prefs = Store.plain(this)
        mem = Memory(this); actions = Actions(this); ai = AiClient(this)
        tts = TextToSpeech(this, this)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        findViewById<ListView>(R.id.chatList).adapter = adapter
        input = findViewById(R.id.input)

        findViewById<Button>(R.id.btnSend).setOnClickListener { send(input.text.toString()) }
        findViewById<Button>(R.id.btnMic).setOnClickListener { listen() }
        findViewById<Button>(R.id.btnMenu).setOnClickListener { menu() }

        loadChat()
        if (msgs.isEmpty()) show("QT: Hii! Main QT Mini hoon 💖 Pehle ⚙ me API key daal do, phir baat karte hain!")
    }

    private fun show(s: String) { adapter.add(s); adapter.notifyDataSetChanged() }

    private fun add(role: String, text: String) {
        msgs.add(role to text)
        show(if (role == "user") "Tum: $text" else "QT: $text")
        saveChat()
        if (role == "assistant") speak(text)
    }

    private fun send(t: String) {
        val text = t.trim(); if (text.isEmpty()) return
        input.setText("")
        add("user", text)

        memoryCommand(text)?.let { add("assistant", it); return }
        actions.tryHandle(text)?.let { add("assistant", it); return }

        val key = Store.secure(this).getString("api_key", "") ?: ""
        if (key.isEmpty()) { show("QT: ⚙ me pehle API key daalo 🙈"); return }
        if (!online()) { show("QT: Internet nahi hai 📵 Abhi sirf basic commands chalenge (open app, timer, alarm, battery, storage)."); return }

        show("QT: ...typing")
        thread {
            try {
                val r = ai.chat(msgs.takeLast(20), mem.all())
                runOnUiThread { removeTyping(); add("assistant", r) }
            } catch (e: Exception) {
                runOnUiThread { removeTyping(); show("QT: Oops, error: ${e.message}") }
            }
        }
    }

    private fun removeTyping() {
        val i = adapter.getPosition("QT: ...typing")
        if (i >= 0) { adapter.remove(adapter.getItem(i)); adapter.notifyDataSetChanged() }
    }

    private fun online(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val c = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun memoryCommand(t: String): String? {
        val m = Regex("^(?:yaad rakho|yaad rakhna|remember)[:\\s]+(.+)$", RegexOption.IGNORE_CASE).find(t) ?: return null
        return if (mem.add(m.groupValues[1])) "Done, yaad rakh liya 🧠✨ (⚙ → Memory me dekh/delete kar sakte ho)"
        else "Password/OTP/PIN jaisi cheez main save nahi karti 🔒"
    }

    private fun showMemory() {
        val l = mem.all()
        if (l.isEmpty()) { Toast.makeText(this, "Memory khaali hai", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Memory (tap = delete)")
            .setItems(l.toTypedArray()) { _, i ->
                AlertDialog.Builder(this).setMessage("Delete?\n${l[i]}")
                    .setPositiveButton("Delete") { _, _ -> mem.removeAt(i) }
                    .setNegativeButton("Cancel", null).show()
            }.setNegativeButton("Close", null).show()
    }

    private fun menu() {
        val voice = prefs.getBoolean("voice", true)
        val items = arrayOf("API key", "Model", "Personality", "Memory dekho/delete",
            "Voice replies: ${if (voice) "ON" else "OFF"}", "Chat clear")
        AlertDialog.Builder(this).setItems(items) { _, i ->
            when (i) {
                0 -> ask("Anthropic API key", "") { Store.secure(this).edit().putString("api_key", it.trim()).apply() }
                1 -> ask("Model", prefs.getString("model", "claude-sonnet-5-5")!!) { prefs.edit().putString("model", it.trim()).apply() }
                2 -> AlertDialog.Builder(this).setItems(Persona.modes.toTypedArray()) { _, k ->
                        prefs.edit().putString("mode", Persona.modes[k]).apply() }.show()
                3 -> showMemory()
                4 -> prefs.edit().putBoolean("voice", !voice).apply()
                5 -> confirmClear()
            }
        }.show()
    }

    private fun ask(title: String, cur: String, ok: (String) -> Unit) {
        val e = EditText(this); e.setText(cur)
        AlertDialog.Builder(this).setTitle(title).setView(e)
            .setPositiveButton("Save") { _, _ -> ok(e.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this).setMessage("Poori chat history delete kar du?")
            .setPositiveButton("Haan") { _, _ -> msgs.clear(); adapter.clear(); saveChat() }
            .setNegativeButton("Nahi", null).show()
    }

    private fun saveChat() {
        val a = JSONArray()
        msgs.takeLast(100).forEach { a.put(JSONObject().put("r", it.first).put("t", it.second)) }
        prefs.edit().putString("chat", a.toString()).apply()
    }

    private fun loadChat() {
        val a = JSONArray(prefs.getString("chat", "[]"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            msgs.add(o.getString("r") to o.getString("t"))
            show((if (o.getString("r") == "user") "Tum: " else "QT: ") + o.getString("t"))
        }
    }

    override fun onInit(status: Int) { ttsReady = status == TextToSpeech.SUCCESS }

    private fun speak(text: String) {
        if (!ttsReady || !prefs.getBoolean("voice", true)) return
        val deva = text.any { it in '\u0900'..'\u097F' }
        tts?.language = if (deva) Locale("hi", "IN") else Locale("en", "IN")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "qt")
    }

    private fun listen() {
        tts?.stop()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Is phone me speech recognition nahi hai", Toast.LENGTH_LONG).show(); return
        }
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(res: Bundle?) {
                    res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { send(it) }
                }
                override fun onError(error: Int) { Toast.makeText(this@MainActivity, "Sunai nahi diya (err $error)", Toast.LENGTH_SHORT).show() }
                override fun onReadyForSpeech(p: Bundle?) { Toast.makeText(this@MainActivity, "Bolo... 🎤", Toast.LENGTH_SHORT).show() }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN"))
        }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (rc == 1 && g.firstOrNull() == PackageManager.PERMISSION_GRANTED) listen()
    }

    override fun onDestroy() { sr?.destroy(); tts?.shutdown(); super.onDestroy() }
}

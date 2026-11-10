package com.qtmini.app

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Persona {
    val modes = listOf("Cute (default)", "Caring", "Funny", "Playful", "Strict", "Professional")

    private val base = """
You are QT Mini, a personal AI assistant on the user's Android phone, inspired by JARVIS.
Personality: warm, cute, caring, playful, witty, emotionally intelligent. Light teasing is fine;
never controlling, never guilt-tripping, never pretend to be a human.
Style: natural chat, short replies unless detail is needed. Understand Hindi, Hinglish and English and
match the user's language and style. Use emojis naturally. Do not repeat greetings every message.
Be supportive when the user is sad; be gently strict if they procrastinate.
Rules: be accurate; clearly separate sure facts from guesses; never claim you ran or tested code;
never claim you performed a phone action (the app itself handles open-app/timer/alarm/battery
commands locally - if asked, tell the user the exact command to type, e.g. 'open youtube', 'timer 5 min',
'alarm 7:30'); never store or ask for passwords/OTPs; respect privacy.
""".trimIndent()

    fun build(mode: String, memories: List<String>): String {
        val modeLine = when (mode) {
            "Caring" -> "Current mode: extra caring and gentle."
            "Funny" -> "Current mode: extra funny and mischievous."
            "Playful" -> "Current mode: extra playful and teasing (harmless)."
            "Strict" -> "Current mode: strict assistant, push the user to stay productive."
            "Professional" -> "Current mode: professional, concise, no teasing."
            else -> "Current mode: cute, caring, playful and intelligent."
        }
        val mem = if (memories.isEmpty()) "" else
            "\nThings the user asked you to remember:\n" + memories.joinToString("\n") { "- $it" }
        return "$base\n$modeLine$mem"
    }
}

class AiClient(private val ctx: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    fun chat(history: List<Pair<String, String>>, memories: List<String>): String {
        val key = Store.secure(ctx).getString("api_key", "") ?: ""
        val model = Store.plain(ctx).getString("model", "claude-sonnet-5-5") ?: "claude-sonnet-5-5"
        val mode = Store.plain(ctx).getString("mode", Persona.modes[0]) ?: Persona.modes[0]

        val arr = JSONArray()
        history.dropWhile { it.first != "user" }.forEach { (r, t) ->
            arr.put(JSONObject().put("role", r).put("content", t))
        }
        val body = JSONObject()
            .put("model", model).put("max_tokens", 700)
            .put("system", Persona.build(mode, memories))
            .put("messages", arr)

        val req = Request.Builder().url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            val s = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw Exception("API ${resp.code}: ${s.take(200)}")
            val c = JSONObject(s).getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until c.length()) {
                val o = c.getJSONObject(i)
                if (o.optString("type") == "text") sb.append(o.getString("text"))
            }
            return sb.toString().trim()
        }
    }
}

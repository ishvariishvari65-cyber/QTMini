package com.qtmini.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

object Store {
    fun secure(ctx: Context): SharedPreferences {
        val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx, "qt_secure", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    fun plain(ctx: Context): SharedPreferences = ctx.getSharedPreferences("qt", Context.MODE_PRIVATE)
}

class Memory(ctx: Context) {
    private val p = Store.plain(ctx)
    private val blocked = Regex("password|passcode|otp|\\bpin\\b|cvv|card number|pass word", RegexOption.IGNORE_CASE)

    fun all(): MutableList<String> {
        val a = JSONArray(p.getString("mem", "[]"))
        return MutableList(a.length()) { a.getString(it) }
    }
    private fun save(l: List<String>) = p.edit().putString("mem", JSONArray(l).toString()).apply()

    fun add(s: String): Boolean {
        if (blocked.containsMatchIn(s)) return false
        val l = all(); l.add(s.trim()); save(l); return true
    }
    fun removeAt(i: Int) { val l = all(); if (i in l.indices) { l.removeAt(i); save(l) } }
}

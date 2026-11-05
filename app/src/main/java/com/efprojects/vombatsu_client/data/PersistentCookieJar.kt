package com.efprojects.vombatsu_client.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.core.content.edit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val cookies = mutableMapOf<String, Cookie>()

    init {
        prefs.getStringSet(KEY, emptySet()).orEmpty().forEach { entry ->
            val (host, raw) = entry.split('\t', limit = 2)
            Cookie.parse("https://$host/".toHttpUrl(), raw)?.let { cookies[it.key] = it }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            if (cookie.isExpired) this.cookies.remove(cookie.key) else this.cookies[cookie.key] = cookie
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (cookies.values.removeAll { it.isExpired }) persist()
        return cookies.values.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        persist()
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
    }

    @Synchronized
    fun syncToWebView() {
        val manager = CookieManager.getInstance()
        cookies.values.filterNot { it.isExpired }.forEach { manager.setCookie("https://${it.domain}/", it.toString()) }
        manager.flush()
    }

    private fun persist() {
        val entries = cookies.values.mapTo(mutableSetOf()) { "${it.domain}\t$it" }
        prefs.edit { putStringSet(KEY, entries) }
    }

    private val Cookie.key get() = "$name|$domain|$path"
    private val Cookie.isExpired get() = expiresAt < System.currentTimeMillis()

    private companion object {
        const val KEY = "cookies"
    }
}

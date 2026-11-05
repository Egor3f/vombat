package com.efprojects.vombatsu_client.auth

import androidx.annotation.StringRes
import com.efprojects.vombatsu_client.R
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val SITE_HOST = "vombat.su"
private const val CALLBACK_PATH = "/oauth/auth/"

enum class OAuthProvider(val value: String, val authorizeUrl: String, @StringRes val title: Int) {
    Yandex(
        value = "yandex",
        authorizeUrl = "https://oauth.yandex.ru/authorize?response_type=code" +
            "&client_id=081bb879c0274fbb80faa2c01a538213" +
            "&redirect_uri=https%3A%2F%2Fvombat.su%2Foauth%2Fauth%2Fyandex",
        title = R.string.sign_in_yandex,
    ),
}

fun oauthCallbackParams(provider: OAuthProvider, url: String): String? {
    val parsed = url.toHttpUrlOrNull() ?: return null
    if (parsed.host != SITE_HOST || parsed.encodedPath != CALLBACK_PATH + provider.value) return null
    return buildJsonObject {
        parsed.queryParameterNames.forEach { name -> put(name, parsed.queryParameter(name)) }
    }.toString()
}

package com.efprojects.vombatsu_client.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OAuthTest {
    @Test
    fun `callback query becomes json params`() {
        assertEquals(
            """{"code":"12 3","cid":"x"}""",
            oauthCallbackParams(OAuthProvider.Yandex, "https://vombat.su/oauth/auth/yandex?code=12%203&cid=x"),
        )
    }

    @Test
    fun `denied callback is still a callback`() {
        assertEquals(
            """{"error":"access_denied"}""",
            oauthCallbackParams(OAuthProvider.Yandex, "https://vombat.su/oauth/auth/yandex?error=access_denied"),
        )
    }

    @Test
    fun `other pages are not callbacks`() {
        assertNull(oauthCallbackParams(OAuthProvider.Yandex, "https://oauth.yandex.ru/authorize?code=1"))
        assertNull(oauthCallbackParams(OAuthProvider.Yandex, "https://vombat.su/oauth/auth/vk?code=1"))
        assertNull(oauthCallbackParams(OAuthProvider.Yandex, "https://evil.su/oauth/auth/yandex?code=1"))
        assertNull(oauthCallbackParams(OAuthProvider.Yandex, "intent://x"))
    }
}

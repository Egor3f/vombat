package com.efprojects.vombatsu_client

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.efprojects.vombatsu_client.actions.ActionsRepository
import com.efprojects.vombatsu_client.actions.MediaUploader
import com.efprojects.vombatsu_client.auth.AuthRepository
import com.efprojects.vombatsu_client.battle.BattleRepository
import com.efprojects.vombatsu_client.content.VideoJobs
import com.efprojects.vombatsu_client.data.PersistentCookieJar
import com.efprojects.vombatsu_client.ignore.IgnoreRepository
import com.efprojects.vombatsu_client.notifications.NotificationsRepository
import com.efprojects.vombatsu_client.settings.AppSettings
import okhttp3.OkHttpClient

class AppGraph(context: Context) {
    val cookieJar = PersistentCookieJar(context)
    val settings = AppSettings(context)

    private val httpClient = ErrorLog.okHttp(OkHttpClient.Builder().cookieJar(cookieJar)).build()

    val apollo: ApolloClient = ErrorLog.apollo(ApolloClient.Builder())
        .serverUrl("https://api.vombat.su/graphql")
        .okHttpClient(httpClient)
        .build()

    val auth = AuthRepository(apollo, cookieJar)
    val actions = ActionsRepository(apollo)
    val notifications = NotificationsRepository(apollo)
    val ignore = IgnoreRepository(apollo)
    val battle = BattleRepository(apollo)
    val uploader = MediaUploader(httpClient, context.contentResolver)
    val videoJobs = VideoJobs(httpClient)
}

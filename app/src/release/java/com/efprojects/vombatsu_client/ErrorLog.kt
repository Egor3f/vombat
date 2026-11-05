package com.efprojects.vombatsu_client

import android.content.Context
import com.apollographql.apollo.ApolloClient
import okhttp3.OkHttpClient

@Suppress("UNUSED_PARAMETER")
object ErrorLog {
    fun init(context: Context) = Unit

    fun write(details: String, error: Throwable? = null) = Unit

    fun okHttp(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder

    fun apollo(builder: ApolloClient.Builder): ApolloClient.Builder = builder
}

package com.efprojects.vombatsu_client

import android.content.Context
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
private const val BODY_PEEK_BYTES = 64L * 1024

object ErrorLog {
    @Volatile
    private var dir: File? = null

    fun init(context: Context) {
        dir = File(context.getExternalFilesDir(null), "errors")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write("Uncaught exception in thread ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
        SingletonImageLoader.setSafe { imageContext ->
            ImageLoader.Builder(imageContext).eventListener(ImageErrors).build()
        }
    }

    fun write(details: String, error: Throwable? = null) {
        val target = dir ?: return
        runCatching {
            target.mkdirs()
            val text = buildString {
                appendLine(details)
                error?.let { appendLine().append(it.stackTraceToString()) }
            }
            File(target, "${FILE_TIME.format(LocalDateTime.now())}-${System.nanoTime()}.log").writeText(text)
        }
    }

    fun okHttp(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder.addInterceptor(HttpErrors)

    fun apollo(builder: ApolloClient.Builder): ApolloClient.Builder = builder.addInterceptor(GraphQlErrors)

    private object HttpErrors : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                write("HTTP ${request.method} ${request.url} failed", e)
                throw e
            }
            if (!response.isSuccessful) {
                val body = response.peekBody(BODY_PEEK_BYTES).string()
                write("HTTP ${request.method} ${request.url} -> ${response.code}\n$body")
            }
            return response
        }
    }

    private object GraphQlErrors : ApolloInterceptor {
        override fun <D : Operation.Data> intercept(
            request: ApolloRequest<D>,
            chain: ApolloInterceptorChain,
        ): Flow<ApolloResponse<D>> = chain.proceed(request).onEach { response ->
            val errors = response.errors
            if (response.exception != null || !errors.isNullOrEmpty()) {
                write(
                    "GraphQL ${request.operation.name()} variables=${request.operation}\nerrors=$errors",
                    response.exception,
                )
            }
        }
    }

    private object ImageErrors : EventListener() {
        override fun onError(request: ImageRequest, result: ErrorResult) {
            write("Image load failed: ${request.data}", result.throwable)
        }
    }
}

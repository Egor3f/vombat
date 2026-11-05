package com.efprojects.vombatsu_client.web

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.net.Uri
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.efprojects.vombatsu_client.ErrorLog
import com.efprojects.vombatsu_client.data.PersistentCookieJar
import com.efprojects.vombatsu_client.post.BackButton

private const val HIDE_SITE_HEADER = """
    (function() {
        var style = document.createElement('style');
        style.textContent = '.top-menu{display:none!important}.top-menu~div.mx-auto{padding-top:12px!important}';
        document.head.appendChild(style);
    })();
"""

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPageScreen(
    url: String,
    @StringRes title: Int,
    cookies: PersistentCookieJar,
    onBack: () -> Unit,
    onNavigate: (String) -> Boolean = { false },
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            factory = { context ->
                cookies.syncToWebView()
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                            onNavigate(request.url.toString())

                        override fun onPageCommitVisible(view: WebView, url: String?) {
                            view.evaluateJavascript(HIDE_SITE_HEADER, null)
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            canGoBack = view.canGoBack()
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            ErrorLog.write("WebView ${request.method} ${request.url} failed: ${error.errorCode} ${error.description}")
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse,
                        ) {
                            ErrorLog.write("WebView ${request.method} ${request.url} -> ${errorResponse.statusCode}")
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                ErrorLog.write("WebView console: ${message.sourceId()}:${message.lineNumber()} ${message.message()}")
                            }
                            return false
                        }

                        override fun onShowFileChooser(
                            view: WebView,
                            callback: ValueCallback<Array<Uri>>,
                            params: FileChooserParams,
                        ): Boolean {
                            fileCallback?.onReceiveValue(null)
                            fileCallback = callback
                            return try {
                                fileChooser.launch(params.createIntent())
                                true
                            } catch (e: ActivityNotFoundException) {
                                ErrorLog.write("File chooser not available", e)
                                fileCallback = null
                                false
                            }
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            onRelease = { view ->
                fileCallback?.onReceiveValue(null)
                fileCallback = null
                view.destroy()
            },
        )
    }
}

package de.menne.htmlordner

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

/**
 * Zeigt eine gespeicherte HTML-Datei im Vollbild an.
 * localStorage bleibt erhalten, so dass Bestwerte in Spielen gespeichert werden.
 */
class ViewerActivity : Activity() {

    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = intent.getStringExtra("path")?.let { File(it) }
        if (file == null || !file.exists()) {
            finish()
            return
        }

        val view = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        web = view
        setContentView(view)
        view.loadUrl(Uri.fromFile(file).toString())
    }

    override fun onBackPressed() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else finish()
    }

    override fun onDestroy() {
        web?.destroy()
        super.onDestroy()
    }
}

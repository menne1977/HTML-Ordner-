package de.menne.htmlordner

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Zeigt eine gespeicherte HTML-Datei im Vollbild an.
 * localStorage bleibt erhalten, so dass Bestwerte in Spielen gespeichert werden.
 *
 * Zusätzlich: window.print() öffnet den Android-Druckdialog (Drucker oder „Als PDF speichern“),
 * Downloads aus der Seite (Blob/Data-Links mit download-Attribut) können gespeichert oder geteilt werden,
 * und Datei-Auswahlfelder (<input type="file">) funktionieren.
 */
class ViewerActivity : Activity() {

    private var web: WebView? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingBytes: ByteArray? = null
    private val reqPick = 41
    private val reqSave = 42

    /** Wird in der Seite als window.AndroidViewer sichtbar. */
    inner class Bridge {
        @JavascriptInterface
        fun print(landscape: Boolean) {
            runOnUiThread { doPrint(landscape) }
        }

        @JavascriptInterface
        fun saveFile(name: String, mime: String, base64: String) {
            val bytes = try {
                Base64.decode(base64, Base64.DEFAULT)
            } catch (e: Exception) {
                return
            }
            runOnUiThread { askSave(safeName(name), mime.ifBlank { "application/octet-stream" }, bytes) }
        }
    }

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
            addJavascriptInterface(Bridge(), "AndroidViewer")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String?) {
                    v.evaluateJavascript(INJECT, null)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    w: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = callback
                    return try {
                        startActivityForResult(params.createIntent(), reqPick)
                        true
                    } catch (e: Exception) {
                        fileCallback = null
                        false
                    }
                }
            }
        }
        web = view
        setContentView(view)
        view.loadUrl(Uri.fromFile(file).toString())
    }

    private fun doPrint(landscape: Boolean) {
        val w = web ?: return
        try {
            val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
            val name = (w.title ?: "").ifBlank { "Dokument" }
            val size = if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape()
            else PrintAttributes.MediaSize.ISO_A4
            val attrs = PrintAttributes.Builder()
                .setMediaSize(size)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
            pm.print(name, w.createPrintDocumentAdapter(name), attrs)
        } catch (e: Exception) {
            toast("Drucken geht auf diesem Gerät nicht")
        }
    }

    private fun askSave(name: String, mime: String, bytes: ByteArray) {
        pendingBytes = bytes
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("In Downloads speichern", "Teilen / an App senden")) { _, which ->
                if (which == 0) saveToDownloads(name, mime, bytes) else share(name, mime, bytes)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun saveToDownloads(name: String, mime: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException()
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                toast("Gespeichert in Downloads: $name")
            } catch (e: Exception) {
                toast("Speichern fehlgeschlagen")
            }
        } else {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mime
                putExtra(Intent.EXTRA_TITLE, name)
            }
            startActivityForResult(i, reqSave)
        }
    }

    private fun share(name: String, mime: String, bytes: ByteArray) {
        try {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val f = File(dir, name).apply { writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, name))
        } catch (e: Exception) {
            toast("Teilen fehlgeschlagen")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqPick) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        } else if (requestCode == reqSave) {
            val bytes = pendingBytes
            val target = data?.data
            if (resultCode == RESULT_OK && bytes != null && target != null) {
                try {
                    contentResolver.openOutputStream(target)?.use { it.write(bytes) }
                    toast("Gespeichert")
                } catch (e: Exception) {
                    toast("Speichern fehlgeschlagen")
                }
            }
        }
    }

    private fun safeName(n: String) = n.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "datei" }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    override fun onBackPressed() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else finish()
    }

    override fun onDestroy() {
        web?.destroy()
        super.onDestroy()
    }

    companion object {
        /** Verbindet window.print() und Downloads mit der App. Läuft nach jedem Seitenaufbau. */
        private const val INJECT = """
(function(){
 if(window.__av)return;window.__av=1;
 function land(){try{return Array.prototype.some.call(document.querySelectorAll('style'),function(s){return /@page[^{]*\{[^}]*landscape/.test(s.textContent)})}catch(e){return false}}
 window.print=function(){
  try{window.dispatchEvent(new Event('beforeprint'))}catch(e){}
  AndroidViewer.print(land());
  setTimeout(function(){try{window.dispatchEvent(new Event('afterprint'))}catch(e){}},800)
 };
 function save(a){
  fetch(a.href).then(function(r){return r.blob()}).then(function(b){
   var fr=new FileReader();
   fr.onload=function(){AndroidViewer.saveFile(a.getAttribute('download')||'datei',b.type||'application/octet-stream',String(fr.result).split(',')[1]||'')};
   fr.readAsDataURL(b)
  })
 }
 function isDl(a){return a&&a.hasAttribute&&a.hasAttribute('download')&&/^(blob|data):/.test(a.href)}
 var oc=HTMLAnchorElement.prototype.click;
 HTMLAnchorElement.prototype.click=function(){if(isDl(this)){save(this);return}oc.call(this)};
 document.addEventListener('click',function(e){var a=e.target.closest&&e.target.closest('a');if(isDl(a)){e.preventDefault();e.stopPropagation();save(a)}},true)
})();
"""
    }
}

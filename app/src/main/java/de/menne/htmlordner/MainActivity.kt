package de.menne.htmlordner

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException

/**
 * Startseite: zeigt alle gespeicherten HTML-Dateien.
 * Dateien werden dauerhaft im internen Speicher der App abgelegt.
 */
class MainActivity : Activity() {

    private val requestPick = 1
    private val files = mutableListOf<File>()
    private lateinit var adapter: ArrayAdapter<String>

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun appDir(): File = File(filesDir, "apps").apply { mkdirs() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titleView = TextView(this).apply {
            text = "Meine HTML-Apps"
            textSize = 22f
            setTextColor(Color.WHITE)
        }

        val addButton = Button(this).apply {
            text = "＋ HTML-Datei hinzufügen"
            setOnClickListener { pickFiles() }
        }

        val hintView = TextView(this).apply {
            text = "Tippen zum Öffnen · lange drücken zum Löschen"
            textSize = 13f
            setTextColor(0xFFAAB2C8.toInt())
        }

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        val listView = ListView(this)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ -> openApp(files[pos]) }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            confirmDelete(files[pos])
            true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1B1F2E.toInt())
            setPadding(dp(16), dp(20), dp(16), dp(16))
        }
        val wrap = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(titleView, wrap)
        root.addView(addButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
            bottomMargin = dp(8)
        })
        root.addView(hintView, wrap)
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(8)
        })
        setContentView(root)

        // Wurde die App über „Öffnen mit“ aus dem Dateimanager gestartet, Datei gleich übernehmen
        if (savedInstanceState == null && intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { importUris(listOf(it)) }
        }

        refresh()
    }

    private fun pickFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, requestPick)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestPick && resultCode == RESULT_OK && data != null) {
            val uris = mutableListOf<Uri>()
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
            }
            data.data?.let { uris.add(it) }
            importUris(uris)
        }
    }

    private fun importUris(uris: List<Uri>) {
        var saved = 0
        for (uri in uris) {
            val name = displayName(uri) ?: "App.html"
            if (!name.endsWith(".html", true) && !name.endsWith(".htm", true)) {
                toast("„$name“ ist keine HTML-Datei")
                continue
            }
            val base = name.substringBeforeLast('.')
                .replace(Regex("[^A-Za-z0-9 _-]"), "_")
                .ifBlank { "App" }
            val target = File(appDir(), "${System.nanoTime()}__$base.html")
            try {
                val input = contentResolver.openInputStream(uri) ?: throw IOException("Kein Zugriff")
                input.use { inp -> target.outputStream().use { out -> inp.copyTo(out) } }
                saved++
            } catch (e: Exception) {
                target.delete()
                toast("„$name“ konnte nicht gelesen werden")
            }
        }
        if (saved > 0) toast("$saved Datei(en) gespeichert")
        refresh()
    }

    private fun displayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun refresh() {
        files.clear()
        files.addAll(
            appDir().listFiles()
                ?.filter { it.isFile && it.name.endsWith(".html") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        )
        adapter.clear()
        adapter.addAll(files.map { titleOf(it) })
        adapter.notifyDataSetChanged()
    }

    private fun titleOf(f: File): String = f.name.substringAfter("__").removeSuffix(".html")

    private fun openApp(f: File) {
        startActivity(Intent(this, ViewerActivity::class.java).putExtra("path", f.absolutePath))
    }

    private fun confirmDelete(f: File) {
        AlertDialog.Builder(this)
            .setTitle("App löschen?")
            .setMessage(titleOf(f))
            .setPositiveButton("Löschen") { _, _ ->
                f.delete()
                refresh()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}

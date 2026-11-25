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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException

/**
 * Startseite: zeigt Ordner und gespeicherte HTML-Dateien des aktuellen Ordners.
 * Alles liegt dauerhaft im internen Speicher der App (filesDir/apps, Unterordner sind erlaubt).
 */
class MainActivity : Activity() {

    private val requestPick = 1
    private val requestExport = 2
    private var pendingExport: File? = null
    private val entries = mutableListOf<File>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var titleView: TextView
    private var current: File? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rootDir(): File = File(filesDir, "apps").apply { mkdirs() }

    private fun currentDir(): File = current ?: rootDir()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        current = savedInstanceState?.getString("dir")?.let { File(it) }?.takeIf { it.isDirectory }

        titleView = TextView(this).apply {
            text = "Meine HTML-Apps"
            textSize = 22f
            setTextColor(Color.WHITE)
        }

        val addButton = Button(this).apply {
            text = "＋ HTML-Datei"
            setOnClickListener { pickFiles() }
        }

        val folderButton = Button(this).apply {
            text = "📁 Neuer Ordner"
            setOnClickListener { showNewFolderDialog() }
        }

        val hintView = TextView(this).apply {
            text = "Ordner und Apps antippen · lange drücken zum Löschen · Zurück geht eine Ebene hoch"
            textSize = 13f
            setTextColor(0xFFAAB2C8.toInt())
        }

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        val listView = ListView(this)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            val f = entries[pos]
            if (f.isDirectory) {
                current = f
                refresh()
            } else {
                openApp(f)
            }
        }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            confirmDelete(entries[pos])
            true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1B1F2E.toInt())
            setPadding(dp(16), dp(20), dp(16), dp(16))
        }
        val wrap = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        buttons.addView(addButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(4)
        })
        buttons.addView(folderButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(4)
        })

        root.addView(titleView, wrap)
        root.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("dir", currentDir().absolutePath)
    }

    override fun onBackPressed() {
        val dir = currentDir()
        if (dir != rootDir()) {
            current = dir.parentFile?.takeIf { it.absolutePath.startsWith(rootDir().absolutePath) } ?: rootDir()
            refresh()
        } else {
            super.onBackPressed()
        }
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
        } else if (requestCode == requestExport) {
            val src = pendingExport
            pendingExport = null
            val target = data?.data
            if (resultCode == RESULT_OK && src != null && target != null) {
                try {
                    val out = contentResolver.openOutputStream(target) ?: throw IOException("Kein Zugriff")
                    out.use { o -> src.inputStream().use { it.copyTo(o) } }
                    toast("„${titleOf(src)}“ exportiert")
                } catch (e: Exception) {
                    toast("Export fehlgeschlagen")
                }
            }
        }
    }

    private fun startExport(f: File) {
        pendingExport = f
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/html"
            putExtra(Intent.EXTRA_TITLE, "${titleOf(f)}.html")
        }
        startActivityForResult(intent, requestExport)
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
            val target = File(currentDir(), "${System.nanoTime()}__$base.html")
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

    private fun showNewFolderDialog() {
        val input = EditText(this).apply {
            hint = "Ordnername"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Neuer Ordner")
            .setView(input)
            .setPositiveButton("Anlegen") { _, _ -> createFolder(input.text.toString()) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun createFolder(raw: String) {
        val name = raw.trim().replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").trimStart('.')
        if (name.isBlank()) {
            toast("Bitte einen Ordnernamen eingeben")
            return
        }
        val target = File(currentDir(), name)
        if (target.exists()) {
            toast("„$name“ gibt es hier schon")
            return
        }
        if (target.mkdirs()) {
            toast("Ordner „$name“ angelegt")
        } else {
            toast("Ordner konnte nicht angelegt werden")
        }
        refresh()
    }

    private fun refresh() {
        val dir = currentDir()
        val children = dir.listFiles() ?: emptyArray()
        entries.clear()
        entries.addAll(children.filter { it.isDirectory }.sortedBy { it.name.lowercase() })
        entries.addAll(
            children.filter { it.isFile && it.name.endsWith(".html") }
                .sortedByDescending { it.lastModified() }
        )
        adapter.clear()
        adapter.addAll(entries.map { if (it.isDirectory) "📁  ${it.name}" else titleOf(it) })
        adapter.notifyDataSetChanged()

        titleView.text = if (dir == rootDir()) "Meine HTML-Apps" else "Meine HTML-Apps / ${dir.name}"
    }

    private fun titleOf(f: File): String = f.name.substringAfter("__").removeSuffix(".html")

    private fun openApp(f: File) {
        startActivity(Intent(this, ViewerActivity::class.java).putExtra("path", f.absolutePath))
    }

    private fun confirmDelete(f: File) {
        val isDir = f.isDirectory
        val builder = AlertDialog.Builder(this)
            .setTitle(if (isDir) "Ordner löschen?" else "App löschen?")
            .setMessage(
                if (isDir) "„${f.name}“ mit allen Dateien darin wird gelöscht."
                else titleOf(f)
            )
            .setPositiveButton("Löschen") { _, _ ->
                if (isDir) f.deleteRecursively() else f.delete()
                refresh()
            }
            .setNegativeButton("Abbrechen", null)
        if (!isDir) {
            builder.setNeutralButton("Exportieren") { _, _ -> startExport(f) }
        }
        builder.show()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}

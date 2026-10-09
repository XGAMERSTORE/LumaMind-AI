package com.xg.lumamind

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.graphics.Typeface
import android.net.Uri
import org.json.JSONArray

class MainActivity : Activity() {
    private lateinit var items: LinearLayout
    private val requestCode = 42
    private val prefs by lazy { getSharedPreferences("documents", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 55, 36, 30)
            setBackgroundColor(Color.rgb(13, 12, 30))
        }
        fun heading(text: String, size: Float): TextView = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, 24)
        }
        root.addView(heading("✦ LumaMind AI", 29f))
        root.addView(heading("Tvoje chytrá knihovna dokumentů", 16f))
        val add = Button(this).apply {
            text = "＋ Přidat dokument"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/jpeg", "image/png"))
                }
                startActivityForResult(intent, requestCode)
            }
        }
        root.addView(add)
        val search = EditText(this).apply {
            hint = "Hledat podle názvu…"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        root.addView(search)
        items = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(items) }
        root.addView(scroll)
        setContentView(root)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh(s.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        refresh()
    }
    private fun documents(): JSONArray = try { JSONArray(prefs.getString("uris", "[]")) } catch (_: Exception) { JSONArray() }
    private fun refresh(query: String = "") {
        items.removeAllViews()
        val docs = documents()
        for (i in 0 until docs.length()) {
            val uri = Uri.parse(docs.getString(i))
            val name = try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else uri.lastPathSegment ?: "Dokument"
                } ?: "Dokument"
            } catch (_: Exception) { uri.lastPathSegment ?: "Dokument" }
            if (!name.contains(query, ignoreCase = true)) continue
            items.addView(Button(this).apply {
                text = "📄  $name"
                setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, contentResolver.getType(uri) ?: "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    } catch (_: Exception) { Toast.makeText(this@MainActivity, "Nelze otevřít soubor", Toast.LENGTH_SHORT).show() }
                }
            })
        }
    }
    @Deprecated("Uses legacy result API for minimal starter")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != this.requestCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        val docs = documents()
        if ((0 until docs.length()).none { docs.getString(it) == uri.toString() }) {
            docs.put(uri.toString())
            prefs.edit().putString("uris", docs.toString()).apply()
        }
        refresh()
    }
}
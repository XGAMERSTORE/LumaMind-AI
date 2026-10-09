package com.xg.lumamind

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("documents", MODE_PRIVATE) }
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var search = ""
    private val pickCode = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 38, 28, 20)
            setBackgroundColor(Color.rgb(17, 15, 33))
        }
        fun title(value: String, size: Float): TextView = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; setPadding(0, 8, 0, 16)
        }
        root.addView(title("✦ LumaMind AI", 28f))
        root.addView(title("Soukromá knihovna dokumentů", 15f))
        root.addView(Button(this).apply {
            text = "＋ Přidat PDF nebo obrázek"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }, pickCode)
            }
        })
        status = title("OCR funguje offline po stažení modelu", 12f)
        root.addView(status)
        val filter = EditText(this).apply {
            hint = "Hledat v názvech a rozpoznaném textu"
            setSingleLine(true)
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
        }
        root.addView(filter)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                search = s.toString(); render()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        render()
    }

    private fun records(): JSONArray = try {
        JSONArray(prefs.getString("records", "[]"))
    } catch (_: Exception) { JSONArray() }

    private fun save(data: JSONArray) {
        prefs.edit().putString("records", data.toString()).apply()
        render()
    }

    private fun filename(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else "Dokument"
        } ?: "Dokument"
    } catch (_: Exception) { "Dokument" }

    private fun render() {
        list.removeAllViews()
        val data = records()
        status.text = "${data.length()} dokumentů • OCR obrázků a první stránky PDF"
        for (index in data.length() - 1 downTo 0) {
            val doc = data.getJSONObject(index)
            val name = doc.optString("name", "Dokument")
            val extracted = doc.optString("text")
            if (!name.contains(search, true) && !extracted.contains(search, true)) continue
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 12, 14, 18)
                setBackgroundColor(Color.rgb(34, 29, 59))
            }
            card.addView(TextView(this).apply {
                text = "📄 $name"; textSize = 17f; setTextColor(Color.WHITE)
            })
            if (extracted.isNotBlank()) card.addView(TextView(this).apply {
                text = extracted.take(180); setTextColor(Color.LTGRAY)
                maxLines = 3
            })
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun action(label: String, callback: () -> Unit) {
                buttons.addView(Button(this).apply {
                    text = label; textSize = 11f; setOnClickListener { callback() }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            action("Otevřít") { open(doc) }
            action("OCR") { recognize(index) }
            action("Sdílet") { share(doc) }
            action("⋮") { options(index) }
            card.addView(buttons)
            list.addView(card)
            list.addView(View(this).apply { minimumHeight = 12 })
        }
    }

    private fun open(doc: JSONObject) {
        try {
            val uri = Uri.parse(doc.getString("uri"))
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (e: Exception) { toast("Soubor nelze otevřít") }
    }

    private fun share(doc: JSONObject) {
        try {
            val uri = Uri.parse(doc.getString("uri"))
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = contentResolver.getType(uri) ?: "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Sdílet dokument"))
        } catch (e: Exception) { toast("Sdílení se nezdařilo") }
    }

    private fun options(index: Int) {
        val doc = records().getJSONObject(index)
        AlertDialog.Builder(this).setItems(arrayOf("Přejmenovat", "Zobrazit OCR text", "Odstranit z knihovny")) { _, which ->
            when (which) {
                0 -> {
                    val field = EditText(this).apply { setText(doc.optString("name")) }
                    AlertDialog.Builder(this).setTitle("Název dokumentu").setView(field)
                        .setPositiveButton("Uložit") { _, _ ->
                            val data = records()
                            data.getJSONObject(index).put("name", field.text.toString().trim())
                            save(data)
                        }.setNegativeButton("Zrušit", null).show()
                }
                1 -> AlertDialog.Builder(this).setTitle("Rozpoznaný text")
                    .setMessage(doc.optString("text", "Nejdříve spusť OCR."))
                    .setPositiveButton("OK", null).show()
                2 -> AlertDialog.Builder(this).setMessage("Odstranit z knihovny? Původní soubor zůstane zachován.")
                    .setPositiveButton("Odstranit") { _, _ ->
                        val data = records()
                        val next = JSONArray()
                        for (i in 0 until data.length()) if (i != index) next.put(data.getJSONObject(i))
                        save(next)
                    }.setNegativeButton("Zrušit", null).show()
            }
        }.show()
    }

    private fun recognize(index: Int) {
        val doc = records().getJSONObject(index)
        val uri = Uri.parse(doc.getString("uri"))
        status.text = "Rozpoznávám text…"
        try {
            val mime = contentResolver.getType(uri) ?: ""
            val input = if (mime == "application/pdf") {
                val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: throw Exception("PDF nelze otevřít")
                val bitmap = descriptor.use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        if (renderer.pageCount == 0) throw Exception("Prázdné PDF")
                        renderer.openPage(0).use { page ->
                            val scale = minOf(2f, 2000f / maxOf(page.width, page.height))
                            val bitmap = android.graphics.Bitmap.createBitmap(
                                maxOf(1, (page.width * scale).toInt()),
                                maxOf(1, (page.height * scale).toInt()),
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
                InputImage.fromBitmap(bitmap, 0)
            } else InputImage.fromFilePath(this, uri)
            recognizer.process(input).addOnSuccessListener { result ->
                val data = records()
                if (index < data.length() && data.getJSONObject(index).optString("uri") == uri.toString()) {
                    data.getJSONObject(index).put("text", result.text)
                    save(data)
                }
                status.text = if (result.text.isBlank()) "Text nebyl nalezen" else "Text rozpoznán"
            }.addOnFailureListener { status.text = "OCR selhalo: ${it.localizedMessage}" }
        } catch (e: Exception) { status.text = "Chyba: ${e.localizedMessage}" }
    }

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()

    @Deprecated("Legacy picker result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: Exception) {}
        val docs = records()
        if ((0 until docs.length()).none { docs.getJSONObject(it).optString("uri") == uri.toString() }) {
            docs.put(JSONObject().put("uri", uri.toString()).put("name", filename(uri)).put("text", ""))
            save(docs)
        }
    }

    override fun onDestroy() { recognizer.close(); super.onDestroy() }
}

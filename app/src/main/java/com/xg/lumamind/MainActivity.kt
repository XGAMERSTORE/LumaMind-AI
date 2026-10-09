package com.xg.lumamind

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
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

    private val bg = Color.rgb(12, 13, 26)
    private val surface = Color.rgb(27, 28, 48)
    private val purple = Color.rgb(151, 111, 255)
    private val muted = Color.rgb(173, 174, 197)
    private lateinit var mainArea: LinearLayout
    private var selectedTab = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 22, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun label(value: String, size: Float = 14f, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

    private fun gap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun roundedButton(text: String, fill: Int, textColor: Int = Color.WHITE, click: () -> Unit): TextView =
        label(text, 14f, textColor, true).apply {
            gravity = android.view.Gravity.CENTER
            background = shape(fill, 16)
            setPadding(dp(12), dp(13), dp(12), dp(13))
            setOnClickListener { click() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(10), dp(18), dp(8))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("✦  LumaMind", 27f, Color.WHITE, true))
        brand.addView(label("INTELIGENTNÍ DOKUMENTY", 10f, purple, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("◉", 25f, purple, true).apply {
            gravity = android.view.Gravity.CENTER
            background = shape(surface, 20)
            setOnClickListener { showAbout() }
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(header)
        gap(root, 22)
        mainArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(mainArea, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            background = shape(surface, 22)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        listOf("⌂\\nDomů", "▤\\nDokumenty", "✦\\nOCR", "⚙\\nNastavení").forEachIndexed { index, title ->
            nav.addView(label(title.replace("\\\\n", "\\n"), 11f, if (index == 0) purple else muted, index == 0).apply {
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(11), 0, dp(11))
                setOnClickListener { selectedTab = index; render() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav)
        setContentView(root)
        render()
    }

    private fun showAbout() {
        AlertDialog.Builder(this).setTitle("LumaMind AI")
            .setMessage("Verze 1.2 • Soukromá knihovna dokumentů. OCR probíhá na zařízení. AI chat a placené funkce zatím nejsou dostupné.")
            .setPositiveButton("Rozumím", null).show()
    }

    private fun picker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, pickCode)
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
        mainArea.removeAllViews()
        val data = records()
        if (selectedTab == 3) {
            mainArea.addView(label("Nastavení", 28f, Color.WHITE, true))
            gap(mainArea, 18)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = shape(surface)
                setPadding(dp(20), dp(20), dp(20), dp(20))
            }
            card.addView(label("Soukromí na prvním místě", 19f, Color.WHITE, true))
            gap(card, 8)
            card.addView(label("Dokumenty zůstávají ve zvoleném úložišti. Rozpoznaný text se ukládá lokálně do aplikace.", 14f, muted))
            gap(card, 16)
            card.addView(roundedButton("O aplikaci", purple) { showAbout() })
            mainArea.addView(card)
            return
        }
        mainArea.addView(label(when (selectedTab) {
            1 -> "Moje dokumenty"
            2 -> "Rozpoznávání textu"
            else -> "Vítej zpátky ✨"
        }, 26f, Color.WHITE, true))
        gap(mainArea, 5)
        mainArea.addView(label(when (selectedTab) {
            2 -> "Otevři dokument a klepni na OCR"
            else -> "Vše důležité na jednom místě"
        }, 14f, muted))
        gap(mainArea, 20)
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(99, 65, 190), Color.rgb(51, 37, 105))).apply {
                cornerRadius = dp(26).toFloat()
            }
            setPadding(dp(22), dp(20), dp(22), dp(20))
        }
        hero.addView(label("TVŮJ DIGITÁLNÍ PROSTOR", 11f, Color.rgb(224, 212, 255), true))
        gap(hero, 9)
        hero.addView(label("${data.length()} dokumentů", 29f, Color.WHITE, true))
        gap(hero, 5)
        hero.addView(label("Uspořádané. Dohledatelné. V bezpečí.", 12f, Color.rgb(231, 224, 255)))
        gap(hero, 17)
        hero.addView(roundedButton("＋  Přidat dokument", Color.WHITE, Color.rgb(67, 43, 125)) { picker() })
        mainArea.addView(hero)
        gap(mainArea, 20)
        val filter = EditText(this).apply {
            hint = "⌕  Hledat v dokumentech a textu"
            setSingleLine(true)
            setText(search)
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            textSize = 14f
            background = shape(surface, 16)
            setPadding(dp(17), dp(12), dp(17), dp(12))
        }
        mainArea.addView(filter)
        gap(mainArea, 17)
        mainArea.addView(label("Knihovna  •  ${data.length()}", 18f, Color.WHITE, true))
        gap(mainArea, 12)
        val scroller = ScrollView(this).apply { fillViewport = true }
        val cards = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroller.addView(cards)
        mainArea.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        fun populate() {
            cards.removeAllViews()
            var found = 0
            for (index in data.length() - 1 downTo 0) {
                val doc = data.getJSONObject(index)
                val name = doc.optString("name", "Dokument")
                val extracted = doc.optString("text")
                if (!name.contains(search, true) && !extracted.contains(search, true)) continue
                found++
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = shape(surface, 20, Color.rgb(43, 43, 70))
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                }
                val top = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                top.addView(label(if (name.endsWith(".pdf", true)) "▣" else "▧", 26f, purple, true).apply {
                    gravity = android.view.Gravity.CENTER
                    background = shape(Color.rgb(50, 41, 83), 14)
                }, LinearLayout.LayoutParams(dp(48), dp(52)))
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, 0, 0)
                }
                info.addView(label(name, 15f, Color.WHITE, true).apply {
                    maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                })
                gap(info, 5)
                info.addView(label(if (extracted.isBlank()) "Připraveno k OCR" else "✓ Text rozpoznán", 12f,
                    if (extracted.isBlank()) muted else Color.rgb(146, 228, 194)))
                top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(top)
                if (extracted.isNotBlank()) {
                    gap(card, 13)
                    card.addView(label(extracted.replace("\\n", " ").take(115), 12f, muted).apply {
                        maxLines = 2
                    })
                }
                gap(card, 14)
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                fun action(text: String, fill: Int, callback: () -> Unit) {
                    actions.addView(roundedButton(text, fill, if (fill == purple) Color.WHITE else Color.rgb(223, 216, 249), callback),
                        LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                }
                action("Otevřít", Color.rgb(54, 49, 82)) { open(doc) }
                action("✦ OCR", purple) { recognize(index) }
                action("Sdílet", Color.rgb(54, 49, 82)) { share(doc) }
                action("•••", Color.rgb(54, 49, 82)) { options(index) }
                card.addView(actions)
                cards.addView(card)
                gap(cards, 12)
            }
            if (found == 0) {
                gap(cards, 30)
                cards.addView(label("Zatím tu nic není", 18f, Color.WHITE, true).apply {
                    gravity = android.view.Gravity.CENTER
                })
                gap(cards, 8)
                cards.addView(label("Přidej PDF nebo obrázek a začni.", 13f, muted).apply {
                    gravity = android.view.Gravity.CENTER
                })
            }
        }
        populate()
        filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                search = s?.toString() ?: ""; populate()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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
        toast("Rozpoznávám text…")
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
                toast(if (result.text.isBlank()) "Text nebyl nalezen" else "Text rozpoznán")
            }.addOnFailureListener { toast("OCR selhalo: ${it.localizedMessage}") }
        } catch (e: Exception) { toast("Chyba: ${e.localizedMessage}") }
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

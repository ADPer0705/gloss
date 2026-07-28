package rocks.dearanayji.gloss

import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Gloss — Offline-first, multi-language translator powered by ML Kit.
 *
 * Users choose source/target language from the spinners. The app builds a
 * Translator for that pair on demand. If the model is not yet downloaded it
 * shows a download banner; once downloaded everything runs fully offline.
 *
 * Tap any word in the input field to see its individual translation.
 * The swap button flips source ↔ target instantly.
 */
class MainActivity : AppCompatActivity() {

    // ── UI refs ────────────────────────────────────────────────────────────
    private lateinit var spinnerSource: Spinner
    private lateinit var spinnerTarget: Spinner
    private lateinit var btnSwap: ImageButton
    private lateinit var inputEditText: EditText
    private lateinit var outputText: TextView
    private lateinit var wordTranslationText: TextView
    private lateinit var statusText: TextView
    private lateinit var statusBadge: TextView
    private lateinit var labelSource: TextView
    private lateinit var labelTarget: TextView
    private lateinit var downloadBanner: LinearLayout
    private lateinit var downloadBannerTitle: TextView
    private lateinit var downloadBannerSubtitle: TextView
    private lateinit var btnDownload: Button
    private lateinit var btnClearInput: TextView
    private lateinit var btnCopyOutput: TextView

    // ── Translator state ───────────────────────────────────────────────────
    private var translator: Translator? = null
    private var modelReady = false
    private val handler = Handler(Looper.getMainLooper())
    private var pendingTranslate: Runnable? = null

    // ── Language catalogue ─────────────────────────────────────────────────
    /**
     * All ML Kit supported languages, displayed as a human-readable name.
     * The [tag] matches the [TranslateLanguage] BCP-47 constants.
     */
    data class Language(val name: String, val tag: String)

    private val languages = listOf(
        Language("Afrikaans",           TranslateLanguage.AFRIKAANS),
        Language("Albanian",            TranslateLanguage.ALBANIAN),
        Language("Arabic",              TranslateLanguage.ARABIC),
        Language("Belarusian",          TranslateLanguage.BELARUSIAN),
        Language("Bengali",             TranslateLanguage.BENGALI),
        Language("Bulgarian",           TranslateLanguage.BULGARIAN),
        Language("Catalan",             TranslateLanguage.CATALAN),
        Language("Chinese (Simplified)",TranslateLanguage.CHINESE),
        Language("Chinese (Traditional)",TranslateLanguage.CHINESE),
        Language("Croatian",            TranslateLanguage.CROATIAN),
        Language("Czech",               TranslateLanguage.CZECH),
        Language("Danish",              TranslateLanguage.DANISH),
        Language("Dutch",               TranslateLanguage.DUTCH),
        Language("English",             TranslateLanguage.ENGLISH),
        Language("Esperanto",           TranslateLanguage.ESPERANTO),
        Language("Estonian",            TranslateLanguage.ESTONIAN),
        Language("Finnish",             TranslateLanguage.FINNISH),
        Language("French",              TranslateLanguage.FRENCH),
        Language("Galician",            TranslateLanguage.GALICIAN),
        Language("Georgian",            TranslateLanguage.GEORGIAN),
        Language("German",              TranslateLanguage.GERMAN),
        Language("Greek",               TranslateLanguage.GREEK),
        Language("Gujarati",            TranslateLanguage.GUJARATI),
        Language("Haitian Creole",      TranslateLanguage.HAITIAN_CREOLE),
        Language("Hebrew",              TranslateLanguage.HEBREW),
        Language("Hindi",               TranslateLanguage.HINDI),
        Language("Hungarian",           TranslateLanguage.HUNGARIAN),
        Language("Icelandic",           TranslateLanguage.ICELANDIC),
        Language("Indonesian",          TranslateLanguage.INDONESIAN),
        Language("Irish",               TranslateLanguage.IRISH),
        Language("Italian",             TranslateLanguage.ITALIAN),
        Language("Japanese",            TranslateLanguage.JAPANESE),
        Language("Kannada",             TranslateLanguage.KANNADA),
        Language("Korean",              TranslateLanguage.KOREAN),
        Language("Latvian",             TranslateLanguage.LATVIAN),
        Language("Lithuanian",          TranslateLanguage.LITHUANIAN),
        Language("Macedonian",          TranslateLanguage.MACEDONIAN),
        Language("Malay",               TranslateLanguage.MALAY),
        Language("Maltese",             TranslateLanguage.MALTESE),
        Language("Marathi",             TranslateLanguage.MARATHI),
        Language("Norwegian",           TranslateLanguage.NORWEGIAN),
        Language("Persian",             TranslateLanguage.PERSIAN),
        Language("Polish",              TranslateLanguage.POLISH),
        Language("Portuguese",          TranslateLanguage.PORTUGUESE),
        Language("Romanian",            TranslateLanguage.ROMANIAN),
        Language("Russian",             TranslateLanguage.RUSSIAN),
        Language("Slovak",              TranslateLanguage.SLOVAK),
        Language("Slovenian",           TranslateLanguage.SLOVENIAN),
        Language("Spanish",             TranslateLanguage.SPANISH),
        Language("Swahili",             TranslateLanguage.SWAHILI),
        Language("Swedish",             TranslateLanguage.SWEDISH),
        Language("Tagalog",             TranslateLanguage.TAGALOG),
        Language("Tamil",               TranslateLanguage.TAMIL),
        Language("Telugu",              TranslateLanguage.TELUGU),
        Language("Thai",                TranslateLanguage.THAI),
        Language("Turkish",             TranslateLanguage.TURKISH),
        Language("Ukrainian",           TranslateLanguage.UKRAINIAN),
        Language("Urdu",                TranslateLanguage.URDU),
        Language("Vietnamese",          TranslateLanguage.VIETNAMESE),
        Language("Welsh",               TranslateLanguage.WELSH),
    )

    private val langNames get() = languages.map { it.name }

    // Default pair: English → Spanish
    private var sourceIndex = languages.indexOfFirst { it.tag == TranslateLanguage.ENGLISH }.coerceAtLeast(0)
    private var targetIndex = languages.indexOfFirst { it.tag == TranslateLanguage.SPANISH }.coerceAtLeast(0)

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupSpinners()
        setupSwapButton()
        setupTextWatcher()
        setupWordTap()
        setupActionButtons()

        // Build the translator for the default pair
        rebuildTranslator()
    }

    override fun onDestroy() {
        super.onDestroy()
        translator?.close()
    }

    // ── View binding ───────────────────────────────────────────────────────

    private fun bindViews() {
        spinnerSource        = findViewById(R.id.spinnerSource)
        spinnerTarget        = findViewById(R.id.spinnerTarget)
        btnSwap              = findViewById(R.id.btnSwap)
        inputEditText        = findViewById(R.id.inputEditText)
        outputText           = findViewById(R.id.outputText)
        wordTranslationText  = findViewById(R.id.wordTranslationText)
        statusText           = findViewById(R.id.statusText)
        statusBadge          = findViewById(R.id.statusBadge)
        labelSource          = findViewById(R.id.labelSource)
        labelTarget          = findViewById(R.id.labelTarget)
        downloadBanner       = findViewById(R.id.downloadBanner)
        downloadBannerTitle  = findViewById(R.id.downloadBannerTitle)
        downloadBannerSubtitle = findViewById(R.id.downloadBannerSubtitle)
        btnDownload          = findViewById(R.id.btnDownload)
        btnClearInput        = findViewById(R.id.btnClearInput)
        btnCopyOutput        = findViewById(R.id.btnCopyOutput)

        // Set hint text colors programmatically (hintTextColor XML attr not supported on EditText/TextView)
        val hintColor = getColor(R.color.text_hint)
        inputEditText.setHintTextColor(hintColor)
        outputText.setHintTextColor(hintColor)
    }

    // ── Spinners ───────────────────────────────────────────────────────────

    private fun setupSpinners() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, langNames).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinnerSource.adapter = adapter
        spinnerTarget.adapter = adapter

        spinnerSource.setSelection(sourceIndex, false)
        spinnerTarget.setSelection(targetIndex, false)

        spinnerSource.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (pos == sourceIndex) return
                sourceIndex = pos
                rebuildTranslator()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerTarget.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (pos == targetIndex) return
                targetIndex = pos
                rebuildTranslator()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    // ── Swap button ────────────────────────────────────────────────────────

    private fun setupSwapButton() {
        btnSwap.setOnClickListener {
            // Animate a quick rotation
            ObjectAnimator.ofFloat(btnSwap, "rotation", 0f, 180f).apply {
                duration = 280
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }

            // Swap indices and text
            val tmp = sourceIndex
            sourceIndex = targetIndex
            targetIndex = tmp

            // Also swap the typed text → translated text
            val inputText = inputEditText.text?.toString().orEmpty()
            val outputSnap = outputText.text?.toString().orEmpty()

            // Update spinners without triggering the listener redundantly
            spinnerSource.setSelection(sourceIndex, true)
            spinnerTarget.setSelection(targetIndex, true)

            // Put translated text into input and re-translate
            if (outputSnap.isNotBlank()) {
                inputEditText.setText(outputSnap)
                inputEditText.setSelection(outputSnap.length)
            }

            rebuildTranslator()
        }
    }

    // ── Translator lifecycle ───────────────────────────────────────────────

    /**
     * Tears down the previous translator and builds a fresh one for the
     * currently selected language pair.
     */
    private fun rebuildTranslator() {
        translator?.close()
        translator = null
        modelReady = false
        outputText.text = ""
        wordTranslationText.visibility = View.GONE

        val src = languages[sourceIndex]
        val tgt = languages[targetIndex]

        labelSource.text = src.name.uppercase()
        labelTarget.text = tgt.name.uppercase()

        if (src.tag == tgt.tag) {
            setStatus("Source and target are the same language.", StatusKind.NEUTRAL)
            hideBanner()
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(src.tag)
            .setTargetLanguage(tgt.tag)
            .build()

        translator = Translation.getClient(options)

        setStatus("Checking model for ${src.name} → ${tgt.name}…", StatusKind.NEUTRAL)
        hideBanner()
        checkModelAndMaybeDownload(autoDownload = false)
    }

    /**
     * Checks if the model is already cached. If yes, marks it ready.
     * If not, shows the download banner (or starts downloading if [autoDownload] is true).
     */
    private fun checkModelAndMaybeDownload(autoDownload: Boolean) {
        val t = translator ?: return
        // ML Kit's downloadModelIfNeeded is a no-op if the model already exists.
        // We use it here as the canonical "is model ready?" gate.
        // If already cached it resolves immediately; if not it starts a download.
        if (autoDownload) {
            startDownload()
        } else {
            // Attempt a probe translate with a trivial string to know if the model
            // is cached without triggering a download.
            t.translate("a")
                .addOnSuccessListener {
                    onModelReady()
                }
                .addOnFailureListener {
                    // Model not available — show the banner
                    showBanner()
                }
        }
    }

    private fun startDownload() {
        val t = translator ?: return
        val src = languages[sourceIndex]
        val tgt = languages[targetIndex]

        setStatus("Downloading ${src.name} model…", StatusKind.DOWNLOADING)
        btnDownload.isEnabled = false
        btnDownload.text = "Downloading…"

        t.downloadModelIfNeeded()
            .addOnSuccessListener {
                onModelReady()
            }
            .addOnFailureListener { e ->
                btnDownload.isEnabled = true
                btnDownload.text = "Retry"
                setStatus("Download failed: ${e.localizedMessage}", StatusKind.ERROR)
            }
    }

    private fun onModelReady() {
        modelReady = true
        hideBanner()
        val src = languages[sourceIndex]
        val tgt = languages[targetIndex]
        setStatus("⬤  ${src.name} → ${tgt.name}  ·  offline ready", StatusKind.READY)
        // Translate whatever is already in the box
        translateFullPhrase(inputEditText.text?.toString().orEmpty())
    }

    private fun showBanner() {
        val src = languages[sourceIndex]
        val tgt = languages[targetIndex]
        downloadBannerTitle.text = "Model not downloaded"
        downloadBannerSubtitle.text = "${src.name} → ${tgt.name}  ·  ~30 MB  ·  one-time"
        downloadBanner.visibility = View.VISIBLE
        setStatus("Download the model to translate offline.", StatusKind.NEUTRAL)

        btnDownload.isEnabled = true
        btnDownload.text = "Download"
        btnDownload.setOnClickListener {
            startDownload()
        }
    }

    private fun hideBanner() {
        downloadBanner.visibility = View.GONE
    }

    // ── Live translation ───────────────────────────────────────────────────

    private fun setupTextWatcher() {
        inputEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                btnClearInput.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                pendingTranslate?.let { handler.removeCallbacks(it) }
                pendingTranslate = Runnable { translateFullPhrase(text) }
                handler.postDelayed(pendingTranslate!!, 350)
            }
        })
    }

    private fun translateFullPhrase(text: String) {
        if (!modelReady || text.isBlank()) {
            outputText.text = ""
            btnCopyOutput.visibility = View.GONE
            return
        }
        translator?.translate(text)
            ?.addOnSuccessListener { translated ->
                outputText.text = translated
                btnCopyOutput.visibility = if (translated.isNotEmpty()) View.VISIBLE else View.GONE
            }
            ?.addOnFailureListener { e ->
                outputText.text = ""
                setStatus("Error: ${e.localizedMessage}", StatusKind.ERROR)
            }
    }

    // ── Word-tap ───────────────────────────────────────────────────────────

    private fun setupWordTap() {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val word = wordAtPosition(inputEditText, e.x, e.y)
                if (!word.isNullOrBlank()) translateWord(word)
                return false
            }
        })
        inputEditText.setOnTouchListener { _, event ->
            gd.onTouchEvent(event)
            false
        }
    }

    private fun wordAtPosition(editText: EditText, x: Float, y: Float): String? {
        val offset = editText.getOffsetForPosition(x, y)
        val text = editText.text?.toString() ?: return null
        if (offset < 0 || offset > text.length) return null
        var start = offset
        var end = offset
        while (start > 0 && text[start - 1].isLetter()) start--
        while (end < text.length && text[end].isLetter()) end++
        return if (start < end) text.substring(start, end) else null
    }

    private fun translateWord(word: String) {
        if (!modelReady) return
        translator?.translate(word)
            ?.addOnSuccessListener { translated ->
                wordTranslationText.text = "  $word  →  $translated  "
                wordTranslationText.visibility = View.VISIBLE
            }
            ?.addOnFailureListener {
                wordTranslationText.visibility = View.GONE
            }
    }

    // ── Action buttons ─────────────────────────────────────────────────────

    private fun setupActionButtons() {
        btnClearInput.setOnClickListener {
            inputEditText.setText("")
            outputText.text = ""
            wordTranslationText.visibility = View.GONE
            btnCopyOutput.visibility = View.GONE
        }

        btnCopyOutput.setOnClickListener {
            val text = outputText.text?.toString() ?: return@setOnClickListener
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("translation", text))
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Status helpers ─────────────────────────────────────────────────────

    enum class StatusKind { NEUTRAL, DOWNLOADING, READY, ERROR }

    private fun setStatus(msg: String, kind: StatusKind) {
        statusText.text = msg
        val (badgeText, badgeColor) = when (kind) {
            StatusKind.READY       -> "⬤  Ready"     to getColor(R.color.status_ready)
            StatusKind.DOWNLOADING -> "⬤  Downloading" to getColor(R.color.status_downloading)
            StatusKind.ERROR       -> "⬤  Error"      to getColor(R.color.status_error)
            StatusKind.NEUTRAL     -> "⬤  Idle"       to getColor(R.color.text_secondary)
        }
        statusBadge.text = badgeText
        statusBadge.setTextColor(badgeColor)
    }
}

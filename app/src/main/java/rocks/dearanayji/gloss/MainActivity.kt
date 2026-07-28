package rocks.dearanayji.gloss

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Minimal EN -> ES translator.
 *
 * - Type in the input box, the Spanish translation streams into the output box
 *   as you type (debounced), fully on-device via ML Kit.
 * - Tap any word you've typed in the input box to see just that word's
 *   translation, without retyping it anywhere.
 *
 * ML Kit's on-device translator uses the same model family as Google
 * Translate's own offline mode, so quality/speed should feel very similar
 * to what you're used to. The only network use is the one-time ~30MB model
 * download on first launch -- after that, everything runs offline.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var inputEditText: EditText
    private lateinit var outputText: TextView
    private lateinit var wordTranslationText: TextView
    private lateinit var statusText: TextView

    private lateinit var translator: Translator
    private var modelReady = false

    private val handler = Handler(Looper.getMainLooper())
    private var pendingTranslate: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputEditText = findViewById(R.id.inputEditText)
        outputText = findViewById(R.id.outputText)
        wordTranslationText = findViewById(R.id.wordTranslationText)
        statusText = findViewById(R.id.statusText)

        // Fixed English -> Spanish for now. Swapping direction or adding more
        // languages later just means building a second Translator the same way.
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
        translator = Translation.getClient(options)
        lifecycle.addObserver(translator) // auto-closes the translator with the Activity

        downloadModel()
        setupLiveTranslate()
        setupWordTap()
    }

    private fun downloadModel() {
        statusText.text = "Downloading Spanish model (one-time, needs internet)…"
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                modelReady = true
                statusText.text = "Offline and ready."
                // In case the user already typed something while it was downloading.
                translateFullPhrase(inputEditText.text.toString())
            }
            .addOnFailureListener { e ->
                statusText.text = "Couldn't download model: ${e.localizedMessage}"
            }
    }

    private fun setupLiveTranslate() {
        inputEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                pendingTranslate?.let { handler.removeCallbacks(it) }
                pendingTranslate = Runnable { translateFullPhrase(text) }
                handler.postDelayed(pendingTranslate!!, 350) // debounce so it doesn't fire every keystroke
            }
        })
    }

    private fun translateFullPhrase(text: String) {
        if (!modelReady || text.isBlank()) {
            outputText.text = ""
            return
        }
        translator.translate(text)
            .addOnSuccessListener { translated -> outputText.text = translated }
            .addOnFailureListener { e -> outputText.text = "Error: ${e.localizedMessage}" }
    }

    private fun setupWordTap() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val word = wordAtPosition(inputEditText, e.x, e.y)
                if (!word.isNullOrBlank()) {
                    translateWord(word)
                }
                return false
            }
        })

        inputEditText.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false // IMPORTANT: don't consume the event, so normal cursor placement/selection still works
        }
    }

    /** Expands out from the tapped character offset to the surrounding word, or null if not on a word. */
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
        translator.translate(word)
            .addOnSuccessListener { translated ->
                wordTranslationText.text = "$word → $translated"
            }
            .addOnFailureListener {
                wordTranslationText.text = ""
            }
    }
}

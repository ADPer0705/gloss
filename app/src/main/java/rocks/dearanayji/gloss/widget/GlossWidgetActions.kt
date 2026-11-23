package rocks.dearanayji.gloss.widget

import android.content.Context
import android.content.Intent
import rocks.dearanayji.gloss.MainActivity

object GlossWidgetActions {
    const val ACTION_SWAP_LANGUAGES = "rocks.dearanayji.gloss.widget.ACTION_SWAP_LANGUAGES"

    const val EXTRA_WIDGET_ACTION = "gloss_widget_action"
    const val WIDGET_ACTION_TRANSLATE = "translate"
    const val WIDGET_ACTION_PASTE = "paste"
    const val WIDGET_ACTION_HISTORY = "history"
    const val WIDGET_ACTION_RESTORE = "restore"

    const val EXTRA_RESTORE_SOURCE_TEXT = "gloss_restore_src_text"
    const val EXTRA_RESTORE_TRANSLATED_TEXT = "gloss_restore_tgt_text"
    const val EXTRA_RESTORE_SOURCE_CODE = "gloss_restore_src_code"
    const val EXTRA_RESTORE_TARGET_CODE = "gloss_restore_tgt_code"

    fun launchIntent(context: Context, action: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WIDGET_ACTION, action)
        }

    fun restorePhraseIntent(
        context: Context,
        sourceText: String,
        translatedText: String,
        sourceCode: String,
        targetCode: String
    ): Intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_RESTORE)
        putExtra(EXTRA_RESTORE_SOURCE_TEXT, sourceText)
        putExtra(EXTRA_RESTORE_TRANSLATED_TEXT, translatedText)
        putExtra(EXTRA_RESTORE_SOURCE_CODE, sourceCode)
        putExtra(EXTRA_RESTORE_TARGET_CODE, targetCode)
    }
}

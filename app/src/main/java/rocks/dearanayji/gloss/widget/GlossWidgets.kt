package rocks.dearanayji.gloss.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import rocks.dearanayji.gloss.R
import rocks.dearanayji.gloss.data.LanguageCatalog
import rocks.dearanayji.gloss.data.PreferencesStore

object GlossWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val quickIds = manager.getAppWidgetIds(ComponentName(context, GlossQuickWidgetProvider::class.java))
        if (quickIds.isNotEmpty()) {
            GlossQuickWidgetProvider.updateWidgets(context, manager, quickIds)
        }
        val phraseIds = manager.getAppWidgetIds(ComponentName(context, GlossPhrasesWidgetProvider::class.java))
        if (phraseIds.isNotEmpty()) {
            GlossPhrasesWidgetProvider.updateWidgets(context, manager, phraseIds)
        }
    }
}

class GlossQuickWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == GlossWidgetActions.ACTION_SWAP_LANGUAGES) {
            val prefs = PreferencesStore(context)
            val oldSrc = prefs.sourceLangCode
            val oldTgt = prefs.targetLangCode
            prefs.setLanguagePair(oldTgt, oldSrc)
        }
    }

    companion object {
        fun updateWidgets(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
            val prefs = PreferencesStore(context)
            val src = LanguageCatalog.byCode(prefs.sourceLangCode)
            val tgt = LanguageCatalog.byCode(prefs.targetLangCode)
            val pairLabel = "${src.name}  →  ${tgt.name}"

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_quick_translate)
                views.setTextViewText(R.id.widget_lang_pair, pairLabel)

                views.setOnClickPendingIntent(
                    R.id.widget_btn_translate,
                    PendingIntent.getActivity(
                        context,
                        101,
                        GlossWidgetActions.launchIntent(context, GlossWidgetActions.WIDGET_ACTION_TRANSLATE),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_paste,
                    PendingIntent.getActivity(
                        context,
                        102,
                        GlossWidgetActions.launchIntent(context, GlossWidgetActions.WIDGET_ACTION_PASTE),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_history,
                    PendingIntent.getActivity(
                        context,
                        103,
                        GlossWidgetActions.launchIntent(context, GlossWidgetActions.WIDGET_ACTION_HISTORY),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_swap,
                    PendingIntent.getBroadcast(
                        context,
                        104,
                        Intent(context, GlossQuickWidgetProvider::class.java).apply {
                            action = GlossWidgetActions.ACTION_SWAP_LANGUAGES
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                manager.updateAppWidget(widgetId, views)
            }
        }
    }
}

class GlossPhrasesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        private val ROW_IDS = intArrayOf(R.id.widget_phrase_row_1, R.id.widget_phrase_row_2, R.id.widget_phrase_row_3)
        private val SRC_IDS = intArrayOf(R.id.widget_phrase_src_1, R.id.widget_phrase_src_2, R.id.widget_phrase_src_3)
        private val TGT_IDS = intArrayOf(R.id.widget_phrase_tgt_1, R.id.widget_phrase_tgt_2, R.id.widget_phrase_tgt_3)

        fun updateWidgets(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
            val prefs = PreferencesStore(context)
            val src = LanguageCatalog.byCode(prefs.sourceLangCode)
            val tgt = LanguageCatalog.byCode(prefs.targetLangCode)
            val pairLabel = "${src.name}  →  ${tgt.name}"
            val history = prefs.getHistory().take(3)

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_recent_phrases)
                views.setTextViewText(R.id.widget_phrases_lang_pair, pairLabel)

                views.setOnClickPendingIntent(
                    R.id.widget_phrases_btn_swap,
                    PendingIntent.getBroadcast(
                        context,
                        201,
                        Intent(context, GlossQuickWidgetProvider::class.java).apply {
                            action = GlossWidgetActions.ACTION_SWAP_LANGUAGES
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                views.setOnClickPendingIntent(
                    R.id.widget_phrases_btn_new,
                    PendingIntent.getActivity(
                        context,
                        202,
                        GlossWidgetActions.launchIntent(context, GlossWidgetActions.WIDGET_ACTION_TRANSLATE),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                if (history.isEmpty()) {
                    views.setViewVisibility(R.id.widget_phrases_empty, View.VISIBLE)
                    views.setOnClickPendingIntent(
                        R.id.widget_phrases_empty,
                        PendingIntent.getActivity(
                            context,
                            203,
                            GlossWidgetActions.launchIntent(context, GlossWidgetActions.WIDGET_ACTION_TRANSLATE),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    ROW_IDS.forEach { views.setViewVisibility(it, View.GONE) }
                } else {
                    views.setViewVisibility(R.id.widget_phrases_empty, View.GONE)
                    for (i in ROW_IDS.indices) {
                        val item = history.getOrNull(i)
                        if (item == null) {
                            views.setViewVisibility(ROW_IDS[i], View.GONE)
                        } else {
                            views.setViewVisibility(ROW_IDS[i], View.VISIBLE)
                            views.setTextViewText(SRC_IDS[i], item.sourceText)
                            views.setTextViewText(TGT_IDS[i], item.translatedText)
                            views.setOnClickPendingIntent(
                                ROW_IDS[i],
                                PendingIntent.getActivity(
                                    context,
                                    210 + i,
                                    GlossWidgetActions.restorePhraseIntent(
                                        context = context,
                                        sourceText = item.sourceText,
                                        translatedText = item.translatedText,
                                        sourceCode = item.sourceCode,
                                        targetCode = item.targetCode
                                    ),
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        }
                    }
                }

                manager.updateAppWidget(widgetId, views)
            }
        }
    }
}

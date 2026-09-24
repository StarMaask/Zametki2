package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.NoteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotesAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val EXTRA_ACTION = "widget_action"
        const val EXTRA_NOTE_ID = "widget_note_id"
        const val ACTION_NEW_NOTE = "action_new_note"

        fun notifyDataChanged(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, NotesAppWidgetProvider::class.java)
            )
            for (id in ids) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = NoteDatabase.getInstance(context)
                val latest = db.noteDao().getLatestActiveNote()

                val views = RemoteViews(context.packageName, R.layout.notes_appwidget)

                // Add Button intent (always creates new note)
                val addIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra(EXTRA_ACTION, ACTION_NEW_NOTE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val addPendingIntent = PendingIntent.getActivity(
                    context,
                    1001 + appWidgetId,
                    addIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_add, addPendingIntent)

                if (latest != null) {
                    val displayTitle = if (latest.title.isNotBlank()) latest.title else "Без названия"
                    val displayContent = if (latest.content.isNotBlank()) {
                        latest.content
                    } else if (latest.checkListJson.isNotBlank()) {
                        "Список задач"
                    } else {
                        "Пустая заметка"
                    }

                    views.setTextViewText(R.id.widget_title, displayTitle)
                    views.setTextViewText(R.id.widget_content, displayContent)

                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        putExtra(EXTRA_NOTE_ID, latest.id)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val openPendingIntent = PendingIntent.getActivity(
                        context,
                        2002 + appWidgetId,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
                } else {
                    views.setTextViewText(R.id.widget_title, "Заметки")
                    views.setTextViewText(R.id.widget_content, "Нажмите +, чтобы быстро записать новую мысль")
                    views.setOnClickPendingIntent(R.id.widget_root, addPendingIntent)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}

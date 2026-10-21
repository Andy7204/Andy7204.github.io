package com.andy.todo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Home screen widget listing what's due; tapping a task marks it done. */
class TodoWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, views(c, it)) }
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }

    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val id = intent.getIntExtra(ReminderReceiver.EXTRA_ID, 0)
            Db.get(c).task(id)?.takeIf { !it.done }?.let { TaskActions.complete(c, it) }
            return
        }
        super.onReceive(c, intent)
    }

    private fun views(c: Context, widgetId: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget)
        val (left, overdue) = counts(c)
        v.setTextViewText(R.id.widget_count, when {
            left == 0 && overdue == 0 -> "All clear"
            overdue > 0 -> "$left today · $overdue overdue"
            else -> "$left left today"
        })

        val adapter = Intent(c, WidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        adapter.data = Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME))
        @Suppress("DEPRECATION")
        v.setRemoteAdapter(R.id.widget_list, adapter)
        v.setEmptyView(R.id.widget_list, R.id.widget_empty)

        val toggle = Intent(c, TodoWidget::class.java).setAction(ACTION_TOGGLE)
        v.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getBroadcast(
            c, 0, toggle, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))

        v.setOnClickPendingIntent(R.id.widget_header, PendingIntent.getActivity(
            c, 1, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        v.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(
            c, 2, Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_ADD)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE))
        return v
    }

    companion object {
        const val ACTION_TOGGLE = "com.andy.todo.WIDGET_TOGGLE"

        /** Open tasks to show: everything overdue or due in the next week, soonest first. */
        fun items(c: Context): List<Task> {
            val horizon = System.currentTimeMillis() + 7 * 24 * 3_600_000L
            return Db.get(c).tasks().filter { !it.done && it.deadline < horizon }
                .sortedWith(compareBy<Task> { it.deadline }).take(30)
        }

        private fun counts(c: Context): Pair<Int, Int> {
            val now = System.currentTimeMillis()
            val open = Db.get(c).tasks().filter { !it.done }
            return open.count { it.deadline >= now && Format.daysFromToday(it.deadline) == 0L } to
                open.count { it.deadline < now }
        }

        fun refresh(c: Context) {
            val mgr = AppWidgetManager.getInstance(c) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(c, TodoWidget::class.java))
            if (ids.isEmpty()) return
            TodoWidget().onUpdate(c, mgr, ids)
        }
    }
}

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val c: Context) : RemoteViewsFactory {
        private var tasks: List<Task> = emptyList()

        override fun onCreate() {}
        override fun onDataSetChanged() {
            tasks = TodoWidget.items(c)
        }
        override fun onDestroy() {}
        override fun getCount() = tasks.size
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = tasks.getOrNull(position)?.id?.toLong() ?: position.toLong()
        override fun hasStableIds() = true

        override fun getViewAt(position: Int): RemoteViews {
            val t = tasks.getOrNull(position) ?: return RemoteViews(c.packageName, R.layout.widget_item)
            val v = RemoteViews(c.packageName, R.layout.widget_item)
            v.setTextViewText(R.id.widget_item_title, t.title)
            val overdue = t.deadline < System.currentTimeMillis()
            v.setTextViewText(R.id.widget_item_due, if (overdue) "Overdue · ${Format.dayTime(c, t.deadline)}"
                else Format.dayTime(c, t.deadline))
            v.setTextColor(R.id.widget_item_due, c.getColor(if (overdue) R.color.danger else R.color.muted))
            v.setViewVisibility(R.id.widget_item_flag, if (t.priority) android.view.View.VISIBLE
                else android.view.View.GONE)
            v.setOnClickFillInIntent(R.id.widget_item, Intent().putExtra(ReminderReceiver.EXTRA_ID, t.id))
            return v
        }
    }
}

package com.andy.todo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity(), TaskActions.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, TICK_MS)
        }
    }
    private val hideBanner = Runnable { hideBanner() }
    private val hideUndo = Runnable { hideUndo() }

    private lateinit var prefs: Prefs
    private lateinit var root: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var filters: LinearLayout
    private lateinit var search: EditText
    private lateinit var heading: TextView
    private lateinit var today: TextView
    private lateinit var progressTitle: TextView
    private lateinit var progressSub: TextView
    private lateinit var progress: ProgressBar
    private lateinit var empty: View
    private lateinit var banner: View
    private lateinit var undo: View
    private lateinit var fab: View

    private var bannerTaskId = 0
    private var undoAction: (() -> Unit)? = null
    private var backCallback: Any? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        root = findViewById(R.id.root)
        scroll = findViewById(R.id.scroll)
        content = findViewById(R.id.content)
        list = findViewById(R.id.list)
        filters = findViewById(R.id.filters)
        search = findViewById(R.id.search)
        heading = findViewById(R.id.heading)
        today = findViewById(R.id.today)
        progressTitle = findViewById(R.id.progressTitle)
        progressSub = findViewById(R.id.progressSub)
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.empty)
        banner = findViewById(R.id.banner)
        undo = findViewById(R.id.undo)
        fab = findViewById(R.id.fab)

        Ui.edgeToEdge(this, root) { bars ->
            root.setPadding(bars.left, 0, bars.right, 0)
            content.setPadding(content.paddingLeft, Ui.dp(this, 24) + bars.top, content.paddingRight, 0)
            scroll.setPadding(0, 0, 0, Ui.dp(this, 110) + bars.bottom)
            (banner.layoutParams as FrameLayout.LayoutParams).topMargin = Ui.dp(this, 12) + bars.top
            (fab.layoutParams as FrameLayout.LayoutParams).bottomMargin = Ui.dp(this, 24) + bars.bottom
            (undo.layoutParams as FrameLayout.LayoutParams).bottomMargin = Ui.dp(this, 28) + bars.bottom
            root.requestLayout()
        }

        fab.setOnClickListener { openSheet(null) }
        findViewById<View>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.searchButton).setOnClickListener {
            if (search.visibility == View.VISIBLE) closeSearch() else openSearch()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = render()
        })
        findViewById<View>(R.id.bannerClose).setOnClickListener { hideBanner() }
        findViewById<View>(R.id.bannerDone).setOnClickListener {
            Db.get(this).task(bannerTaskId)?.takeIf { !it.done }?.let { complete(it) }
            hideBanner()
        }
        findViewById<View>(R.id.undoAction).setOnClickListener {
            undoAction?.invoke()
            hideUndo()
        }

        Reminders.ensureChannels(this)
        Reminders.syncAll(this)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        TaskActions.listener = this
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        TaskActions.listener = null
        handler.removeCallbacks(tick)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        render()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_ADD) {
            intent.action = null
            root.post { openSheet(null) }
        }
    }

    @Deprecated("Used below Android 13")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        if (search.visibility == View.VISIBLE) closeSearch() else super.onBackPressed()
    }

    // TaskActions.Listener

    override fun onTasksChanged() = render()

    override fun onReminder(task: Task) {
        bannerTaskId = task.id
        findViewById<TextView>(R.id.bannerTitle).text = task.title
        findViewById<TextView>(R.id.bannerSub).text = Format.due(this, task.deadline)
        handler.removeCallbacks(hideBanner)
        if (banner.visibility != View.VISIBLE) {
            banner.visibility = View.VISIBLE
            banner.translationY = -300f
            banner.alpha = 0f
            banner.animate().translationY(0f).alpha(1f).setDuration(280).start()
        }
        handler.postDelayed(hideBanner, BANNER_MS)
        render()
    }

    // Search

    private fun openSearch() {
        search.visibility = View.VISIBLE
        search.requestFocus()
        getSystemService(InputMethodManager::class.java).showSoftInput(search, 0)
        if (Build.VERSION.SDK_INT >= 33) {
            val cb = android.window.OnBackInvokedCallback { closeSearch() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            backCallback = cb
        }
    }

    private fun closeSearch() {
        search.setText("")
        search.visibility = View.GONE
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(search.windowToken, 0)
        if (Build.VERSION.SDK_INT >= 33) {
            (backCallback as? android.window.OnBackInvokedCallback)?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
            backCallback = null
        }
    }

    // Rendering

    private fun render() {
        val db = Db.get(this)
        val all = db.tasks()
        val lists = db.lists()
        val listById = lists.associateBy { it.id }
        val now = System.currentTimeMillis()
        val query = search.text.toString().trim()

        var filter = prefs.filter
        val filterListId = filter.removePrefix("list:").toIntOrNull()
        if (filter.startsWith("list:") && listById[filterListId] == null) {
            filter = "all"
            prefs.filter = filter
        }

        today.text = DateFormat.format("EEEE, MMMM d", now)
        heading.text = when {
            query.isNotEmpty() -> "Search"
            filter == "today" -> "Today"
            filter.startsWith("list:") -> listById[filterListId]?.name ?: "My Tasks"
            else -> "My Tasks"
        }
        renderFilters(lists, filter)

        val visible = when {
            query.isNotEmpty() -> all.filter { it.title.contains(query, ignoreCase = true) }
            filter == "today" -> all.filter {
                if (it.done) Format.daysFromToday(it.doneAt) == 0L
                else it.deadline < now || Format.daysFromToday(it.deadline) == 0L
            }
            filter.startsWith("list:") -> all.filter { it.listId == filterListId }
            else -> all
        }

        val byPriority = compareByDescending<Task> { it.priority }.thenBy { it.deadline }
        val overdue = visible.filter { !it.done && it.deadline < now }.sortedWith(byPriority)
        val dueToday = visible.filter { !it.done && it.deadline >= now && Format.daysFromToday(it.deadline) == 0L }
            .sortedWith(byPriority)
        val upcoming = visible.filter { !it.done && it.deadline >= now && Format.daysFromToday(it.deadline) > 0L }
            .sortedBy { it.deadline }
        val done = visible.filter { it.done }.sortedByDescending { it.doneAt }

        renderProgress(visible.size, done.size, overdue.size, dueToday.size, filter)

        list.removeAllViews()
        section("Overdue", overdue, listById, filter)
        section("Today", dueToday, listById, filter)
        section("Upcoming", upcoming, listById, filter)
        if (done.isNotEmpty()) {
            val header = sectionHeader("Completed", done.size)
            header.findViewById<TextView>(R.id.sectionAction).apply {
                visibility = View.VISIBLE
                text = if (prefs.showCompleted || query.isNotEmpty()) "Hide" else "Show"
                setOnClickListener {
                    prefs.showCompleted = !prefs.showCompleted
                    render()
                }
            }
            header.findViewById<TextView>(R.id.sectionAction2).apply {
                visibility = View.VISIBLE
                text = "Clear"
                setOnClickListener { clearCompleted(done) }
            }
            list.addView(header)
            if (prefs.showCompleted || query.isNotEmpty()) done.forEach { list.addView(card(it, listById, filter)) }
        }

        empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        val emptyTitle = findViewById<TextView>(R.id.emptyTitle)
        val emptySub = findViewById<TextView>(R.id.emptySub)
        val emoji = findViewById<TextView>(R.id.emptyEmoji)
        when {
            query.isNotEmpty() -> {
                emoji.text = "🔍"
                emptyTitle.text = "No matches"
                emptySub.text = "Nothing called “$query”"
            }
            all.isEmpty() -> {
                emoji.text = "🌿"
                emptyTitle.text = "All clear"
                emptySub.text = "Tap + to add a task.\nTry typing “Call mom tomorrow 6pm”."
            }
            filter == "today" -> {
                emoji.text = "☀️"
                emptyTitle.text = "Nothing due today"
                emptySub.text = "Enjoy your day"
            }
            else -> {
                emoji.text = "📭"
                emptyTitle.text = "This list is empty"
                emptySub.text = "Tap + to add a task to it"
            }
        }
    }

    private fun renderProgress(total: Int, done: Int, overdue: Int, dueToday: Int, filter: String) {
        progress.progress = if (total == 0) 0 else done * 100 / total
        when {
            total == 0 -> {
                progressTitle.text = "Nothing planned"
                progressSub.text = "Add a task to get started"
            }
            done == total -> {
                progressTitle.text = "All done! 🎉"
                progressSub.text = "$total ${if (total == 1) "task" else "tasks"} completed"
            }
            else -> {
                progressTitle.text = "$done of $total done"
                progressSub.text = listOfNotNull(
                    if (overdue > 0) "$overdue overdue" else null,
                    if (dueToday > 0 && filter != "today") "$dueToday due today" else null,
                    if (dueToday > 0 && filter == "today") "$dueToday to go" else null,
                ).joinToString(" · ").ifEmpty { "You're on track" }
            }
        }
    }

    private fun renderFilters(lists: List<TaskList>, filter: String) {
        filters.removeAllViews()
        filters.addView(Ui.chip(this, "All", filter == "all") { setFilter("all") })
        filters.addView(Ui.chip(this, "Today", filter == "today") { setFilter("today") })
        lists.forEach { l ->
            filters.addView(Ui.chip(this, l.name, filter == "list:${l.id}", l.color) { setFilter("list:${l.id}") }
                .apply {
                    setOnLongClickListener {
                        ListDialog(this@MainActivity, l) { render() }.show()
                        true
                    }
                })
        }
        filters.addView(Ui.chip(this, "+ New list", false) {
            ListDialog(this, null) { created ->
                created?.let { setFilter("list:${it.id}") } ?: render()
            }.show()
        })
    }

    private fun setFilter(f: String) {
        prefs.filter = f
        if (search.visibility == View.VISIBLE) closeSearch()
        render()
        scroll.smoothScrollTo(0, 0)
    }

    private fun sectionHeader(name: String, count: Int): View =
        layoutInflater.inflate(R.layout.item_section, list, false).apply {
            findViewById<TextView>(R.id.sectionTitle).text = "$name  ·  $count"
        }

    private fun section(name: String, tasks: List<Task>, lists: Map<Int, TaskList>, filter: String) {
        if (tasks.isEmpty()) return
        list.addView(sectionHeader(name, tasks.size))
        tasks.forEach { list.addView(card(it, lists, filter)) }
    }

    private fun card(t: Task, lists: Map<Int, TaskList>, filter: String): View {
        val v = layoutInflater.inflate(R.layout.item_task, list, false)
        val card = v.findViewById<View>(R.id.card)
        val title = v.findViewById<TextView>(R.id.title)
        val due = v.findViewById<TextView>(R.id.due)
        val meta = v.findViewById<TextView>(R.id.meta)
        val check = v.findViewById<ImageView>(R.id.check)
        val swipeBg = v.findViewById<View>(R.id.swipeBg)
        val iconStart = v.findViewById<View>(R.id.swipeIconStart)
        val iconEnd = v.findViewById<View>(R.id.swipeIconEnd)

        title.text = t.title
        val left = t.deadline - System.currentTimeMillis()
        val (fg, bg) = when {
            t.done -> R.color.muted to R.color.chip
            left < 0 -> R.color.danger to R.color.danger_soft
            left < 24 * 3_600_000L -> R.color.warn to R.color.warn_soft
            else -> R.color.primary to R.color.primary_soft
        }
        due.text = if (t.done) "Done · ${Format.dayTime(this, t.doneAt.takeIf { it > 0 } ?: t.deadline)}"
            else Format.due(this, t.deadline)
        due.setTextColor(getColor(fg))
        due.background = Ui.rounded(getColor(bg), 100f)
        val clock = getDrawable(R.drawable.ic_clock)!!.mutate()
        clock.setTint(getColor(fg))
        clock.setBounds(0, 0, Ui.dp(this, 13), Ui.dp(this, 13))
        due.setCompoundDrawablesRelative(clock, null, null, null)

        val inList = lists[t.listId]?.takeIf { filter != "list:${it.id}" }
        val metaParts = listOfNotNull(
            if (t.repeat != Repeat.NONE) "↻ ${t.repeat.label}" else null,
            inList?.name,
        )
        meta.visibility = if (metaParts.isEmpty()) View.GONE else View.VISIBLE
        meta.text = metaParts.joinToString("  ·  ")
        meta.setCompoundDrawablesRelativeWithIntrinsicBounds(inList?.let { Ui.dot(this, it.color, 8) }, null, null, null)

        if (t.done) {
            check.setBackgroundResource(R.drawable.bg_check_done)
            check.setImageResource(R.drawable.ic_check)
            title.paintFlags = title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            title.alpha = 0.5f
        } else {
            check.setBackgroundResource(R.drawable.bg_check)
            check.setImageDrawable(null)
            title.paintFlags = title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            title.alpha = 1f
        }
        v.findViewById<View>(R.id.flag).visibility = if (t.priority && !t.done) View.VISIBLE else View.GONE
        v.findViewById<View>(R.id.bell).visibility =
            if (!t.done && t.remindBefore != Task.NO_REMINDER) View.VISIBLE else View.GONE

        v.findViewById<View>(R.id.checkTouch).setOnClickListener {
            check.animate().scaleX(0.6f).scaleY(0.6f).setDuration(90).withEndAction {
                check.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                if (t.done) TaskActions.reopen(this, t) else complete(t)
            }.start()
        }
        card.setOnClickListener { openSheet(t) }
        card.setOnTouchListener(Swipe(card, { dir ->
            swipeBg.visibility = View.VISIBLE
            val color = if (dir > 0) R.color.success else R.color.danger
            swipeBg.background = Ui.rounded(getColor(color), Ui.dp(this, 18).toFloat())
            iconStart.visibility = if (dir > 0) View.VISIBLE else View.INVISIBLE
            iconEnd.visibility = if (dir < 0) View.VISIBLE else View.INVISIBLE
        }, { dir ->
            if (dir > 0) {
                if (t.done) TaskActions.reopen(this, t) else complete(t)
            } else {
                delete(t)
            }
        }))
        return v
    }

    // Actions

    private fun complete(t: Task) {
        val c = TaskActions.complete(this, t)
        val next = c.nextId.takeIf { it != 0 }?.let { Db.get(this).task(it) }
        showUndo(if (next != null) "Done · next ${Format.dayTime(this, next.deadline)}" else "Completed “${t.title}”") {
            TaskActions.undoComplete(this, c)
        }
    }

    private fun delete(t: Task) {
        val copy = t.copy()
        TaskActions.delete(this, t.id)
        showUndo("Deleted “${t.title}”") { TaskActions.restore(this, listOf(copy)) }
    }

    private fun clearCompleted(done: List<Task>) {
        val copies = done.map { it.copy() }
        TaskActions.deleteAll(this, done)
        showUndo("Cleared ${copies.size} completed") { TaskActions.restore(this, copies) }
    }

    private fun openSheet(existing: Task?) {
        val listId = prefs.filter.removePrefix("list:").toIntOrNull() ?: 0
        TaskSheet(this, existing, listId, onSave = { task ->
            TaskActions.save(this, task)
            if (task.remindBefore != Task.NO_REMINDER) askForReminderPermissions()
        }, onDelete = { task -> delete(task) }).show()
    }

    /** Asks for notification and exact-alarm access the first time they would be useful. */
    private fun askForReminderPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && !prefs.askedNotifications &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.askedNotifications = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        if (Build.VERSION.SDK_INT >= 31 && !prefs.askedExactAlarms && !Reminders.canScheduleExact(this)) {
            prefs.askedExactAlarms = true
            AlertDialog.Builder(this)
                .setTitle("Allow precise reminders?")
                .setMessage("So reminders arrive exactly on time, allow Todo to set alarms. " +
                    "Without it, Android may deliver them a few minutes late.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Allow") { _, _ -> openExactAlarmSettings(this) }
                .show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == REQUEST_NOTIFICATIONS) askForReminderPermissions()
    }

    // Banner and undo bar

    private fun hideBanner() {
        handler.removeCallbacks(hideBanner)
        if (banner.visibility != View.VISIBLE) return
        banner.animate().translationY(-300f).alpha(0f).setDuration(220)
            .withEndAction { banner.visibility = View.GONE }.start()
    }

    private fun showUndo(text: String, action: () -> Unit) {
        undoAction = action
        findViewById<TextView>(R.id.undoText).text = text
        handler.removeCallbacks(hideUndo)
        if (undo.visibility != View.VISIBLE) {
            undo.visibility = View.VISIBLE
            undo.translationY = Ui.dp(this, 80).toFloat()
            undo.animate().translationY(0f).setDuration(200).start()
        }
        handler.postDelayed(hideUndo, UNDO_MS)
    }

    private fun hideUndo() {
        handler.removeCallbacks(hideUndo)
        undoAction = null
        if (undo.visibility != View.VISIBLE) return
        undo.animate().translationY(Ui.dp(this, 80).toFloat()).setDuration(180)
            .withEndAction { undo.visibility = View.GONE }.start()
    }

    companion object {
        const val ACTION_ADD = "com.andy.todo.ADD"
        private const val TICK_MS = 30_000L
        private const val BANNER_MS = 10_000L
        private const val UNDO_MS = 4_500L
        private const val REQUEST_NOTIFICATIONS = 1

        fun openExactAlarmSettings(c: Context) {
            if (Build.VERSION.SDK_INT < 31) return
            c.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${c.packageName}")))
        }
    }
}


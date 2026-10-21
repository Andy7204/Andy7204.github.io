package com.andy.todo

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

/** Bottom sheet for adding or editing a task, with live quick-add parsing of the title. */
class TaskSheet(
    private val activity: Activity,
    existing: Task?,
    defaultListId: Int,
    private val onSave: (Task) -> Unit,
    private val onDelete: (Task) -> Unit,
) {
    private val isNew = existing == null
    private val task = existing?.copy() ?: Task(listId = defaultListId)
    private val lists = Db.get(activity).lists()

    // What the fields hold before any quick-add text is applied
    private val baseDeadline: Long = existing?.deadline ?: defaultDeadline()
    private val baseRepeat = task.repeat
    private val baseListId = task.listId
    private val basePriority = task.priority

    // Once the user sets a field by hand, typed text no longer overrides it
    private var deadlineTouched = false
    private var repeatTouched = false
    private var listTouched = false
    private var priorityTouched = false

    private val cal = Calendar.getInstance().apply { timeInMillis = baseDeadline }
    private var parsed: QuickAddParser.Result? = null

    private lateinit var dialog: Dialog
    private lateinit var input: EditText
    private lateinit var detected: TextView
    private lateinit var dateView: TextView
    private lateinit var timeView: TextView
    private lateinit var chipsRepeat: LinearLayout
    private lateinit var chipsRemind: LinearLayout
    private lateinit var chipsList: LinearLayout
    private lateinit var priority: Switch

    fun show() {
        dialog = Dialog(activity, R.style.Sheet)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.sheet_task)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            @Suppress("DEPRECATION")
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                if (isNew) WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE else 0)
        }

        input = dialog.findViewById(R.id.input)
        detected = dialog.findViewById(R.id.detected)
        dateView = dialog.findViewById(R.id.pickDate)
        timeView = dialog.findViewById(R.id.pickTime)
        chipsRepeat = dialog.findViewById(R.id.chipsRepeat)
        chipsRemind = dialog.findViewById(R.id.chipsRemind)
        chipsList = dialog.findViewById(R.id.chipsList)
        priority = dialog.findViewById(R.id.priority)
        val delete = dialog.findViewById<View>(R.id.delete)

        dialog.findViewById<TextView>(R.id.sheetTitle).text = if (isNew) "New task" else "Edit task"
        delete.visibility = if (isNew) View.INVISIBLE else View.VISIBLE
        if (!isNew) {
            input.setText(task.title)
            input.setSelection(input.length())
        }
        input.requestFocus()
        priority.isChecked = task.priority

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = applyQuickAdd()
        })

        dateView.setOnClickListener {
            DatePickerDialog(activity, { _, y, m, d ->
                cal.set(y, m, d)
                deadlineTouched = true
                refresh()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        timeView.setOnClickListener {
            TimePickerDialog(activity, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, min)
                cal.set(Calendar.SECOND, 0)
                deadlineTouched = true
                refresh()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), DateFormat.is24HourFormat(activity)).show()
        }
        dialog.findViewById<View>(R.id.priorityRow).setOnClickListener { priority.toggle() }
        priority.setOnCheckedChangeListener { _, checked ->
            task.priority = checked
            priorityTouched = true
        }

        dialog.findViewById<View>(R.id.save).setOnClickListener { save() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save()
                true
            } else false
        }
        delete.setOnClickListener {
            dialog.dismiss()
            onDelete(task)
        }

        refresh()
        dialog.show()
    }

    private fun save() {
        val raw = input.text.toString().trim()
        val title = parsed?.takeIf { it.found }?.title ?: raw
        if (raw.isEmpty()) {
            input.error = "Give your task a name"
            return
        }
        task.title = title
        task.deadline = cal.timeInMillis
        dialog.dismiss()
        onSave(task)
    }

    /** Re-reads the title and fills in any fields the user hasn't set by hand. */
    private fun applyQuickAdd() {
        val r = QuickAddParser.parse(input.text.toString(), LocalDateTime.now(), lists.map { it.name })
        parsed = r
        if (!deadlineTouched) {
            cal.timeInMillis = baseDeadline
            if (r.date != null || r.time != null) {
                val base = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(baseDeadline), ZoneId.systemDefault())
                val dt = LocalDateTime.of(r.date ?: base.toLocalDate(), r.time ?: base.toLocalTime())
                cal.timeInMillis = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        if (!repeatTouched) task.repeat = r.repeat ?: baseRepeat
        if (!listTouched) task.listId = r.listName?.let { n -> lists.firstOrNull { it.name == n }?.id } ?: baseListId
        if (!priorityTouched) {
            task.priority = r.priority || basePriority
            priority.setOnCheckedChangeListener(null)
            priority.isChecked = task.priority
            priority.setOnCheckedChangeListener { _, checked ->
                task.priority = checked
                priorityTouched = true
            }
        }

        if (r.found) {
            val bits = mutableListOf<String>()
            if (r.date != null || r.time != null) bits += Format.dayTime(activity, cal.timeInMillis)
            r.repeat?.let { bits += "Repeats ${it.label.lowercase()}" }
            r.listName?.let { bits += it }
            if (r.priority) bits += "High priority"
            detected.text = "✨ " + bits.joinToString(" · ") + "  →  “${r.title}”"
            detected.visibility = View.VISIBLE
        } else {
            detected.visibility = View.GONE
        }
        refresh()
    }

    private fun refresh() {
        dateView.text = Format.day(cal.timeInMillis)
        timeView.text = Format.time(activity, cal.timeInMillis)

        chipsRepeat.removeAllViews()
        Repeat.entries.forEach { r ->
            chipsRepeat.addView(Ui.chip(activity, r.label, task.repeat == r) {
                task.repeat = r
                repeatTouched = true
                refresh()
            })
        }

        chipsRemind.removeAllViews()
        REMINDERS.forEach { m ->
            chipsRemind.addView(Ui.chip(activity, Format.reminderChip(m), task.remindBefore == m) {
                task.remindBefore = m
                refresh()
            })
        }

        chipsList.removeAllViews()
        chipsList.addView(Ui.chip(activity, "No list", task.listId == 0) {
            task.listId = 0
            listTouched = true
            refresh()
        })
        lists.forEach { l ->
            chipsList.addView(Ui.chip(activity, l.name, task.listId == l.id, l.color) {
                task.listId = l.id
                listTouched = true
                refresh()
            })
        }
    }

    companion object {
        private val REMINDERS = intArrayOf(Task.NO_REMINDER, 0, 15, 60, 1440)

        /** The top of the hour, at least an hour from now. */
        fun defaultDeadline(): Long = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

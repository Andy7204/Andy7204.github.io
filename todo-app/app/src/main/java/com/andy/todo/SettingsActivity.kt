package com.andy.todo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        val root = findViewById<View>(R.id.root)
        val content = findViewById<View>(R.id.content)
        Ui.edgeToEdge(this, root) { bars ->
            content.setPadding(Ui.dp(this, 20) + bars.left, Ui.dp(this, 16) + bars.top,
                Ui.dp(this, 20) + bars.right, Ui.dp(this, 32) + bars.bottom)
        }

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.rowNotifications).setOnClickListener { onNotificationsTapped() }
        findViewById<View>(R.id.rowExact).setOnClickListener { MainActivity.openExactAlarmSettings(this) }
        findViewById<View>(R.id.rowSummary).setOnClickListener {
            prefs.summaryEnabled = !prefs.summaryEnabled
            Reminders.scheduleSummary(this)
            refresh()
        }
        findViewById<View>(R.id.rowSummaryTime).setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                prefs.summaryMinutes = h * 60 + m
                Reminders.scheduleSummary(this)
                refresh()
            }, prefs.summaryMinutes / 60, prefs.summaryMinutes % 60, DateFormat.is24HourFormat(this)).show()
        }
        findViewById<View>(R.id.rowExport).setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "todo-backup-${LocalDate.now()}.json"), REQUEST_EXPORT)
        }
        findViewById<View>(R.id.rowImport).setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream")),
                REQUEST_IMPORT)
        }
        findViewById<View>(R.id.rowPrivacy).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
        }
        findViewById<TextView>(R.id.subExport).text = "Save all tasks and lists to a file"
        findViewById<TextView>(R.id.subImport).text = "Replace current tasks with a backup file"
        findViewById<TextView>(R.id.subPrivacy).text = "No account, no ads, nothing leaves your phone"
        findViewById<TextView>(R.id.subVersion).text =
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun notificationsAllowed(): Boolean =
        getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()

    private fun refresh() {
        findViewById<TextView>(R.id.subNotifications).text =
            if (notificationsAllowed()) "Allowed" else "Off — tap to allow reminders"
        val exactRow = findViewById<View>(R.id.rowExact)
        if (Build.VERSION.SDK_INT >= 31) {
            findViewById<TextView>(R.id.subExact).text = if (Reminders.canScheduleExact(this))
                "Allowed — reminders arrive on the minute" else "Off — reminders may be a few minutes late. Tap to allow"
        } else {
            exactRow.visibility = View.GONE
        }
        findViewById<Switch>(R.id.switchSummary).isChecked = prefs.summaryEnabled
        findViewById<TextView>(R.id.subSummary).text = "A note about tasks left for today"
        val minutes = prefs.summaryMinutes
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, minutes / 60)
            set(java.util.Calendar.MINUTE, minutes % 60)
        }
        findViewById<TextView>(R.id.subSummaryTime).text = Format.time(this, cal.timeInMillis)
        findViewById<View>(R.id.rowSummaryTime).alpha = if (prefs.summaryEnabled) 1f else 0.4f
        findViewById<View>(R.id.rowSummaryTime).isEnabled = prefs.summaryEnabled
    }

    private fun onNotificationsTapped() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            return
        }
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        refresh()
    }

    @Deprecated("Framework activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQUEST_EXPORT -> try {
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(Backup.export(this).toByteArray()) }
                val count = Db.get(this).tasks().size
                Toast.makeText(this, "Backed up $count ${if (count == 1) "task" else "tasks"}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't save the backup", Toast.LENGTH_LONG).show()
            }
            REQUEST_IMPORT -> {
                val contents = try {
                    val text = contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    Backup.parse(text)
                } catch (e: IllegalArgumentException) {
                    AlertDialog.Builder(this).setTitle("Can't restore").setMessage(e.message)
                        .setPositiveButton("OK", null).show()
                    return
                } catch (e: Exception) {
                    Toast.makeText(this, "Couldn't read that file", Toast.LENGTH_LONG).show()
                    return
                }
                val current = Db.get(this).tasks().size
                AlertDialog.Builder(this)
                    .setTitle("Restore ${contents.tasks.size} tasks?")
                    .setMessage(if (current > 0) "This replaces the $current tasks currently in the app." else null)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore") { _, _ ->
                        Backup.restore(this, contents)
                        Toast.makeText(this, "Restored", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }
    }

    companion object {
        private const val REQUEST_EXPORT = 10
        private const val REQUEST_IMPORT = 11
        const val PRIVACY_URL = "https://andy7204.github.io/todo-app/privacy-policy.html"
    }
}

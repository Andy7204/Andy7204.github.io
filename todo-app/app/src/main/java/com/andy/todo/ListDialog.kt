package com.andy.todo

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Create, rename, recolour or delete a list. */
class ListDialog(
    private val activity: Activity,
    existing: TaskList?,
    private val onChanged: (TaskList?) -> Unit,
) {
    private val list = existing?.copy() ?: TaskList(color = Palette.LIST_COLORS[Db.get(activity).lists().size % Palette.LIST_COLORS.size])
    private val isNew = existing == null

    fun show() {
        val dialog = Dialog(activity, R.style.Sheet)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_list)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        val name = dialog.findViewById<EditText>(R.id.listName)
        val colors = dialog.findViewById<LinearLayout>(R.id.listColors)
        val delete = dialog.findViewById<TextView>(R.id.listDelete)
        dialog.findViewById<TextView>(R.id.listDialogTitle).text = if (isNew) "New list" else "Edit list"
        delete.visibility = if (isNew) View.INVISIBLE else View.VISIBLE
        name.setText(list.name)
        name.setSelection(name.length())
        name.requestFocus()

        fun drawColors() {
            colors.removeAllViews()
            val size = Ui.dp(activity, 34)
            Palette.LIST_COLORS.forEach { color ->
                val swatch = FrameLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, size, 1f)
                    setOnClickListener {
                        list.color = color
                        drawColors()
                    }
                    contentDescription = "Colour"
                }
                val circle = ImageView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                    }
                    if (color == list.color) {
                        setImageResource(R.drawable.ic_check)
                        setColorFilter(Color.WHITE)
                        val p = Ui.dp(activity, 7)
                        setPadding(p, p, p, p)
                    }
                }
                swatch.addView(circle)
                colors.addView(swatch)
            }
        }
        drawColors()

        dialog.findViewById<View>(R.id.listSave).setOnClickListener {
            val n = name.text.toString().trim()
            if (n.isEmpty()) {
                name.error = "Give your list a name"
                return@setOnClickListener
            }
            list.name = n
            Db.get(activity).saveList(list)
            dialog.dismiss()
            onChanged(list)
        }
        delete.setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle("Delete “${list.name}”?")
                .setMessage("Its tasks are kept and moved out of the list.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    Db.get(activity).deleteList(list.id)
                    dialog.dismiss()
                    TaskActions.changed(activity)
                    onChanged(null)
                }
                .show()
        }
        dialog.show()
    }
}

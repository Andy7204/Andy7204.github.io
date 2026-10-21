package com.andy.todo

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Horizontal swipe on a card that sits inside a vertically scrolling parent.
 * Taps still reach the card's click listener; only a clear sideways drag becomes a swipe.
 */
class Swipe(
    private val card: View,
    private val onDirection: (Int) -> Unit,
    private val onSwiped: (Int) -> Unit,
) : View.OnTouchListener {
    private val slop = ViewConfiguration.get(card.context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(card.context).scaledMinimumFlingVelocity * 4
    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    private var velocity: VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                swiping = false
                velocity = VelocityTracker.obtain().also { it.addMovement(e) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(e)
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (!swiping && abs(dx) > slop * 1.5f && abs(dx) > abs(dy) * 1.5f) {
                    swiping = true
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    // Cancel the pressed state so the click doesn't fire on release.
                    val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
                    v.onTouchEvent(cancel)
                    cancel.recycle()
                }
                if (swiping) {
                    card.translationX = dx
                    card.alpha = 1f - minOf(0.4f, abs(dx) / card.width)
                    onDirection(if (dx > 0) 1 else -1)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasSwiping = swiping
                swiping = false
                val vt = velocity
                velocity = null
                if (wasSwiping) {
                    vt?.computeCurrentVelocity(1000)
                    val vx = vt?.xVelocity ?: 0f
                    vt?.recycle()
                    val dx = card.translationX
                    val dir = if (dx > 0) 1 else -1
                    val flung = abs(vx) > minFling && (vx > 0) == (dx > 0)
                    if (e.actionMasked == MotionEvent.ACTION_UP && (abs(dx) > card.width * 0.35f || flung)) {
                        card.animate().translationX(dir * card.width.toFloat()).alpha(0f).setDuration(160)
                            .withEndAction { onSwiped(dir) }.start()
                    } else {
                        card.animate().translationX(0f).alpha(1f).setDuration(160).start()
                    }
                    return true
                }
                vt?.recycle()
            }
        }
        return false
    }
}

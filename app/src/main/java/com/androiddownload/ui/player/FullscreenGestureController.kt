package com.androiddownload.ui.player

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

class FullscreenGestureController(
    context: Context,
    private val touchTarget: View,
    private val onSingleTap: () -> Unit,
    private val onDoubleTap: (tapX: Float, width: Int) -> Unit
) {
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            onSingleTap()
            return true
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            val width = touchTarget.width.takeIf { it > 0 } ?: return true
            onDoubleTap(event.x, width)
            return true
        }
    })

    fun attach() {
        touchTarget.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    fun detach() {
        touchTarget.setOnTouchListener(null)
    }
}

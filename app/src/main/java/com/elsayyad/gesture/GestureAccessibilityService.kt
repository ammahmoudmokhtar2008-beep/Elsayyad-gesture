package com.elsayyad.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.media.AudioManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class GestureAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun performGesture(command: GestureCommand) {
        when (command) {
            GestureCommand.SCROLL_UP, GestureCommand.SCROLL_DOWN -> {
                val metrics = resources.displayMetrics
                val x = metrics.widthPixels * 0.5f
                val fromY = metrics.heightPixels * if (command == GestureCommand.SCROLL_UP) 0.72f else 0.28f
                val toY = metrics.heightPixels * if (command == GestureCommand.SCROLL_UP) 0.28f else 0.72f
                val path = Path().apply { moveTo(x, fromY); lineTo(x, toY) }
                val stroke = GestureDescription.StrokeDescription(path, 0, 260)
                dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
            }
            GestureCommand.VOLUME_UP, GestureCommand.VOLUME_DOWN -> {
                val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (command == GestureCommand.VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
            }
        }
    }

    companion object {
        @Volatile var instance: GestureAccessibilityService? = null
            private set
    }
}

enum class GestureCommand { SCROLL_UP, SCROLL_DOWN, VOLUME_UP, VOLUME_DOWN }

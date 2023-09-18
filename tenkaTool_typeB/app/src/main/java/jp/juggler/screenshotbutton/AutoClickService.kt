package jp.juggler.screenshotbutton;

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Binder
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Created on 2018/9/28.
 * By nesto
 */

var autoClickService: AutoClickService? = null

class AutoClickService : AccessibilityService() {
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onServiceConnected() {
        super.onServiceConnected()
        autoClickService = this
    }
    inner class LocalBinder : Binder() {
        // 바인딩된 클라이언트에게 서비스 객체를 반환
        fun getService(): AutoClickService = this@AutoClickService
    }

    fun click(x: Int, y: Int) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val builder = GestureDescription.Builder()
        val gestureDescription = builder
            .addStroke(GestureDescription.StrokeDescription(path, 10, 10))
            .build()
        dispatchGesture(gestureDescription, null, null)
        Log.i("click", "click $x, $y")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        autoClickService = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        autoClickService = null
        super.onDestroy()
    }
}
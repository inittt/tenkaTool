package jp.juggler.screenshotbutton;

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Binder
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent


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
        val posX = x.toFloat()
        var posY = y.toFloat()
        // 스마트폰 디바이스라면 상태바 크기 더하기
        if (LocalDataManager.getInt(applicationContext, "IS_EMULATOR") != 1) {
            posY += getStatusBarHeight(applicationContext)
        }

        path.moveTo(posX, posY)
        val builder = GestureDescription.Builder()
        val gestureDescription = builder
            .addStroke(GestureDescription.StrokeDescription(path, 10, 10))
            .build()
        dispatchGesture(gestureDescription, null, null)
        Log.i("click", "click $posX, $posY")
    }

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    fun getStatusBarHeight(context: Context): Int{
        var statusbarHeight = 0
        val resourceId: Int = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusbarHeight = resources.getDimensionPixelSize(resourceId)
        }
        return statusbarHeight
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
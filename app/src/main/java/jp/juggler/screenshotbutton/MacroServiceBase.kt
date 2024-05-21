package jp.juggler.screenshotbutton

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import androidx.core.app.NotificationCompat
import jp.juggler.util.*
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max


abstract class MacroServiceBase(val isVideo: Boolean) : Service(), View.OnClickListener, View.OnTouchListener {

    companion object {
        val logCompanion = LogCategory("${App1.tagPrefix}/MacroService")

        const val EXTRA_MACRO_INTENT = "macroIntent"
        private var macroJob: WeakReference<Job>? = null

        private val serviceList = LinkedList<WeakReference<MacroServiceBase>>()

        private fun addActiveService(service: MacroServiceBase) {
            serviceList.add(WeakReference(service))
        }
        private fun removeActiveService(service: MacroServiceBase) {
            val it = serviceList.iterator()
            while (it.hasNext()) {
                val ref = it.next()
                val s = ref.get()
                if (s == null || s == service) it.remove()
            }
        }
        fun getServices() = serviceList.mapNotNull { it.get() }
        fun showButtonAll() {
            runOnMainThread {
                logCompanion.d("showButtonAll")
                getServices().forEach { it.showButton() }
            }
        }
        fun getStopReason(serviceClass: Class<*>): String? {
            val key = "StopReason/${serviceClass.name}"
            return App1.pref.getString(key, null)
        }
        fun setStopReason(service: MacroServiceBase, reason: String?) {
            val key = "StopReason/${service.javaClass.name}"
            App1.pref.edit().apply {
                if (reason == null) {
                    remove(key)
                } else {
                    putString(key, reason)
                }
            }.apply()
        }
    }

    private val log = LogCategory("${App1.tagPrefix}/${this.javaClass.simpleName}")
    private val notificationId = NOTIFICATION_ID_RUNNING_MACRO
    private val pendingIntentRequestCodeRestart = PI_CODE_RESTART_MACRO
    protected val context: Context get() = this
    protected lateinit var notificationManager: NotificationManager
    private lateinit var windowManager: WindowManager

    private lateinit var btnOnOff: MyImageButton
    private lateinit var btnGoStop: MyImageButton
    private lateinit var btnDelete: MyImageButton
    private lateinit var onOffParam: WindowManager.LayoutParams
    private lateinit var goStopParam: WindowManager.LayoutParams
    private lateinit var deleteParam: WindowManager.LayoutParams
    private lateinit var onOffView: View
    private lateinit var goStopView: View
    private lateinit var deleteView: View

    private var startLpX = 0
    private var startLpY = 0
    private var startLpXGoStop = 0
    private var startLpYGoStop = 0
    private var startMotionX = 0f
    private var startMotionY = 0f
    private var startMotionXGoStop = 0f
    private var startMotionYGoStop = 0f
    private var isDragging = false
    private var isDraggingGoStop = false
    private var draggingThreshold = 0f
    private var draggingThresholdGoStop = 0f
    private var maxX = 0
    private var maxY = 0
    private var goStopMaxX = 0
    private var goStopMaxY = 0
    private var bigButtonSize = 0
    private var smallButtonSize = 0

    private var macroOn = false
    private var macroGo = false

    @Volatile
    protected var isDestroyed = false
    override fun onBind(intent: Intent): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        log.d("onTaskRemoved")
        setStopReason(this, "onTaskRemoved")

        // restart service
        systemService<AlarmManager>(this)?.let { manager ->
            val pendingIntent = PendingIntent.getService(
                this,
                pendingIntentRequestCodeRestart,
                Intent(this, this.javaClass)
                    .apply {
                        val old = rootIntent?.getParcelableExtraCompat<Intent>(
                            EXTRA_MACRO_INTENT
                        )
                        if (old != null) putExtra(EXTRA_MACRO_INTENT, old)
                    },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onLowMemory() {
        log.d("onLowMemory")
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        log.d("onTrimMemory $level")
        super.onTrimMemory(level)
    }

    @SuppressLint("ClickableViewAccessibility", "RtlHardcoded")
    override fun onCreate() {
        log.d("onCreate")
        App1.prepareAppState(context)
        addActiveService(this)
        setStopReason(this, null)

        super.onCreate()

        notificationManager = systemService(context)!!
        windowManager = systemService(context)!!

        @SuppressLint("InflateParams")
        deleteView = LayoutInflater.from(context).inflate(R.layout.service_overlay_x, null)
        @SuppressLint("InflateParams")
        onOffView = LayoutInflater.from(context).inflate(R.layout.service_overlay_onoff, null)

        startForeground(notificationId, createRunningNotification(false))
        btnDelete = deleteView.findViewById(R.id.btnDelete)
        btnOnOff = onOffView.findViewById(R.id.btnOnOff)

        btnOnOff.setOnClickListener(this)
        btnOnOff.setOnTouchListener(this)
        onOffParam = initParam()
        deleteParam = initParam()

        val dm = resources.displayMetrics
        bigButtonSize = Pref.ipCameraButtonSize(App1.pref).toFloat().dp2px(dm)
        smallButtonSize = Pref.smallButton(App1.pref).toFloat().dp2px(dm)

        setDeletePos(dm)
        btnDelete.windowLayoutParams = deleteParam
        windowManager.addView(deleteView, deleteParam)

        setOnOffPos()
        btnOnOff.windowLayoutParams = onOffParam
        windowManager.addView(onOffView, onOffParam)

        btnDelete.setBackgroundResource(R.drawable.btn_bg_round_red)
        btnDelete.setImageResource(R.drawable.ic_delete)
        btnDelete.visibility = View.GONE

        macroOn = false
        macroGo = false
        showButtonAll()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setGoStopBtn() {
        @SuppressLint("InflateParams")
        goStopView = LayoutInflater.from(context).inflate(R.layout.service_overlay_gostop, null)

        btnGoStop = goStopView.findViewById(R.id.btnGoStop)

        btnGoStop.setOnClickListener(this)
        btnGoStop.setOnTouchListener(this)

        goStopParam = initParam()

        btnGoStop.setBackgroundResource(R.drawable.btn_bg_round)
        btnGoStop.setImageResource(R.drawable.btn_play)

        setGoStopPos(resources.displayMetrics)
        btnGoStop.windowLayoutParams = goStopParam
        windowManager.addView(goStopView, goStopParam)
    }

    private fun initParam() : WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            0,
            0,
            if (Build.VERSION.SDK_INT >= API_APPLICATION_OVERLAY) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.LEFT or Gravity.TOP
        }
    }

    private fun setOnOffPos() {
        val dm = resources.displayMetrics
        onOffParam.width = bigButtonSize
        onOffParam.height = bigButtonSize
        onOffParam.x = clipInt(0, dm.widthPixels - bigButtonSize, dm.widthPixels - bigButtonSize * 3/2)
        onOffParam.y = clipInt(0, dm.heightPixels - bigButtonSize, dm.heightPixels * 2/5)
    }
    private fun setDeletePos(dm: DisplayMetrics) {
        deleteParam.width = bigButtonSize
        deleteParam.height = bigButtonSize
        deleteParam.x = clipInt(0, dm.widthPixels - bigButtonSize, (dm.widthPixels - bigButtonSize)/2)
        deleteParam.y = clipInt(0, dm.heightPixels - bigButtonSize, dm.heightPixels - bigButtonSize*2)
    }
    private fun setGoStopPos(dm: DisplayMetrics) {
        goStopParam.width = smallButtonSize
        goStopParam.height = smallButtonSize

        val mx = LocalDataManager.getInt(applicationContext, "macroX")
        val my = LocalDataManager.getInt(applicationContext, "macroY")
        if (mx != -1 && my != -1) {
            goStopParam.x = mx
            goStopParam.y = my
        } else {
            goStopParam.x = clipInt(0, dm.widthPixels - smallButtonSize, dm.widthPixels * 1 / 2)
            goStopParam.y = clipInt(0, dm.heightPixels - smallButtonSize, dm.heightPixels * 1 / 2)
        }
    }

    override fun onDestroy() {
        log.i("onDestroy start. stopReason=${getStopReason(this.javaClass)}")
        removeActiveService(this)
        isDestroyed = true
        timer?.cancel()
        windowManager.removeView(onOffView)
        try {
            windowManager.removeView(goStopView)
        } catch(_: Exception) {}
        stopForeground(true)

        if (getServices().isEmpty()) {
            log.i("onDestroy: captureJob join start")
            runBlocking {
                macroJob?.get()?.join()
            }
            log.i("onDestroy: captureJob join end")
            Capture.release("service.onDestroy")
        }

        showButtonAll()

        super.onDestroy()
        log.i("onDestroy end")
    }

    fun showButton() {
        if (isDestroyed) return
        btnOnOff.setBackgroundResource(R.drawable.btn_bg_round)
        btnOnOff.setImageResource(R.drawable.btn_on)
        macroOn = false
        macroGo = false
        stopMacro()
    }

    private fun updateDragging(
        ev: MotionEvent,
        save: Boolean = false
    ): Boolean {
        val deltaX = ev.rawX - startMotionX
        val deltaY = ev.rawY - startMotionY
        if (!isDragging) {
            if (max(abs(deltaX), abs(deltaY)) < draggingThreshold) return false
            isDragging = true
            showButtonAll()
        }
        onOffParam.x = clipInt(0, maxX, startLpX + deltaX.toInt())
        onOffParam.y = clipInt(0, maxY, startLpY + deltaY.toInt())

        windowManager.updateViewLayout(onOffView, onOffParam)
        return true
    }

    private fun updateDraggingGoStop(
        ev: MotionEvent,
        save: Boolean = false
    ): Boolean {
        val deltaX = ev.rawX - startMotionXGoStop
        val deltaY = ev.rawY - startMotionYGoStop
        if (!isDraggingGoStop) {
            if (max(abs(deltaX), abs(deltaY)) < draggingThresholdGoStop) return false
            isDraggingGoStop = true
        }
        goStopParam.x = clipInt(0, goStopMaxX, startLpXGoStop + deltaX.toInt())
        goStopParam.y = clipInt(0, goStopMaxY + smallButtonSize, startLpYGoStop + deltaY.toInt())

        windowManager.updateViewLayout(goStopView, goStopParam)
        return true
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    override fun onClick(v: View?) {
        if (v != null) {
            when (v.id) {
                R.id.btnOnOff -> {
                    macroOn = !macroOn
                    macroGo = false
                    stopMacro()

                    Log.d("macro", "macro$macroOn")
                    if (macroOn) {
                        setGoStopBtn()

                        btnOnOff.setBackgroundResource(R.drawable.btn_bg_round_blue)
                        btnOnOff.setImageResource(R.drawable.btn_on)

                        btnOnOff.setOnTouchListener(null)
                    } else {
                        try {
                            windowManager.removeView(goStopView)
                        } catch (_: Exception) {}
                        btnOnOff.setBackgroundResource(R.drawable.btn_bg_round)
                        btnOnOff.setImageResource(R.drawable.btn_on)
                        btnOnOff.setOnTouchListener(this)
                    }
                }

                R.id.btnGoStop -> {
                    macroGo = !macroGo
                    if (macroGo) {
                        try {
                            windowManager.removeView(goStopView)
                        } catch (_: Exception) {}
                        startMacro()
                    }
                }
            }
        }
    }

    override fun onTouch(v: View?, ev: MotionEvent): Boolean {
        when(v?.id) {
            R.id.btnOnOff -> {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val dm = resources.displayMetrics
                        startLpX = onOffParam.x
                        startLpY = onOffParam.y
                        startMotionX = ev.rawX
                        startMotionY = ev.rawY
                        draggingThreshold = resources.displayMetrics.density * 8f
                        maxX = dm.widthPixels - bigButtonSize
                        maxY = dm.heightPixels - bigButtonSize

                        isDragging = false

                        btnDelete.visibility = View.VISIBLE
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        updateDragging(ev)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!updateDragging(ev, save = true)) {
                            v.performClick()
                        }
                        btnDelete.visibility = View.GONE

                        val dist = bigButtonSize * 2 / 3
                        if (deleteParam.x - dist < onOffParam.x && onOffParam.x < deleteParam.x + dist &&
                            deleteParam.y - dist < onOffParam.y && onOffParam.y < deleteParam.y + dist
                        ) {
                            stopWithReason("StopButton")
                        }
                        return true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        if (!updateDragging(ev, save = true)) {
                            showButtonAll()
                        }
                        return true
                    }
                }
                return false
            }
            R.id.btnGoStop -> {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val dm = resources.displayMetrics
                        startLpXGoStop = goStopParam.x
                        startLpYGoStop = goStopParam.y
                        startMotionXGoStop = ev.rawX
                        startMotionYGoStop = ev.rawY
                        draggingThresholdGoStop = resources.displayMetrics.density * 8f
                        goStopMaxX = dm.widthPixels - smallButtonSize
                        goStopMaxY = dm.heightPixels - smallButtonSize

                        isDraggingGoStop = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        updateDraggingGoStop(ev)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        LocalDataManager.setInt(applicationContext, "macroX", goStopParam.x)
                        LocalDataManager.setInt(applicationContext, "macroY", goStopParam.y)
                        if (!updateDraggingGoStop(ev, save = true)) {
                            v.performClick()
                        }
                        log.i("" + (goStopParam.x+smallButtonSize/2) + ", " + (goStopParam.y+smallButtonSize/2))
                        return true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        if (!updateDraggingGoStop(ev, save = true)) {
                            CaptureServiceBase.showButtonAll()
                        }
                        return true
                    }
                }
                return false
            }
            else -> {return false}
        }
    }

    private var timer: Timer? = null
    private fun stopMacro() {
        if (autoClickService == null) Log.i("alert", "autoClickService is null")
        Log.i("macro", "macro off")
        timer?.cancel()
    }

    private fun startMacro() {
        if (autoClickService == null) Log.i("alert", "autoClickService is null")

        btnGoStop.setOnClickListener(null)
        btnGoStop.isClickable = false
        btnGoStop.isFocusable = false

        val x = goStopParam.x + smallButtonSize/2
        val y = goStopParam.y + smallButtonSize/2
        Log.i("macro", "macro on x : $x, y : $y")

        timer = fixedRateTimer(initialDelay = 0, period = 1000) {
            autoClickService?.click(x, y)
        }
    }

    ///////////////////////////////////////////////////////////////

    private fun createRunningNotification(isRecording: Boolean): Notification {


        val channelId = when {
            isVideo -> NOTIFICATION_CHANNEL_VIDEO
            else -> NOTIFICATION_CHANNEL_STILL
        }
        createNotificationChannel(channelId)

        return NotificationCompat.Builder(context, channelId)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .apply {
                val actionIndexes = arrangeNotification(this, isRecording)
                setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(*actionIndexes)
                )

            }
            .build()


    }

    fun stopWithReason(reason: String) {
        setStopReason(this, reason)
        stopSelf()
    }

    private val uiHandler = Handler(Looper.getMainLooper())

    abstract fun createNotificationChannel(channelId: String)

    abstract fun arrangeNotification(
        builder: NotificationCompat.Builder,
        isRecording: Boolean
    ): IntArray

}

fun <T : MacroServiceBase, R : Any?> T?.runOnService(
    context: Context,
    notificationId: Int? = null,
    block: T.() -> R
): R? = when (this) {
    null -> {
        MacroServiceBase.logCompanion.eToast(context, false, "service not running.")
        if (notificationId != null) {
            systemService<NotificationManager>(context)?.cancel(notificationId)
        }
        null
    }
    else -> block.invoke(this)
}
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
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max


abstract class CaptureServiceBase(
    val isVideo: Boolean
) : Service(), View.OnClickListener, View.OnTouchListener {

    companion object {
        val logCompanion = LogCategory("${App1.tagPrefix}/CaptureService")

        const val EXTRA_SCREEN_CAPTURE_INTENT = "screenCaptureIntent"

        private var captureJob: WeakReference<Job>? = null

        private var isVideoCaptureJob = false

        fun isCapturing() = captureJob?.get()?.isActive == true

        private val serviceList = LinkedList<WeakReference<CaptureServiceBase>>()

        private fun addActiveService(service: CaptureServiceBase) {
            serviceList.add(WeakReference(service))
        }

        private fun removeActiveService(service: CaptureServiceBase) {
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

        fun setStopReason(service: CaptureServiceBase, reason: String?) {
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

    val fpCameraButtonX = when {
        isVideo -> Pref.fpCameraButtonXVideo
        else -> Pref.fpCameraButtonXStill
    }

    val fpCameraButtonY = when {
        isVideo -> Pref.fpCameraButtonYVideo
        else -> Pref.fpCameraButtonYStill
    }

    private val notificationId = when {
        isVideo -> NOTIFICATION_ID_RUNNING_VIDEO
        else -> NOTIFICATION_ID_RUNNING_STILL
    }

    private val pendingIntentRequestCodeRestart = when {
        isVideo -> PI_CODE_RESTART_VIDEO
        else -> PI_CODE_RESTART_STILL
    }

    protected val context: Context
        get() = this

    protected lateinit var notificationManager: NotificationManager
    private lateinit var windowManager: WindowManager

    private lateinit var btnPlay: MyImageButton
    private lateinit var btnDelete: MyImageButton
    private lateinit var textBox: MyTextBox
    private lateinit var btnEdit: MyImageButton
    private lateinit var popup: MyEditPopup
    private lateinit var editBox: EditText
    private lateinit var editFinishBtn: Button

    private lateinit var playParam: WindowManager.LayoutParams
    private lateinit var deleteParam: WindowManager.LayoutParams
    private lateinit var textParam: WindowManager.LayoutParams
    private lateinit var editParam: WindowManager.LayoutParams
    private lateinit var popupParam: WindowManager.LayoutParams

    private lateinit var playView: View
    private lateinit var deleteView: View
    private lateinit var textView: View
    private lateinit var editView: View
    private lateinit var popupView: View

    private var startLpX = 0
    private var startLpY = 0
    private var startMotionX = 0f
    private var startMotionY = 0f
    private var isDragging = false
    private var draggingThreshold = 0f
    private var maxX = 0
    private var maxY = 0
    private var bigButtonSize = 0
    private var smallButtonSize = 0
    private var txt = "tmp"
    private var textBoxOn = false

    private lateinit var tenkaRecruit: TenkaRecruit

    @Volatile
    protected var isDestroyed = false

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleIntent(intent)
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
                            EXTRA_SCREEN_CAPTURE_INTENT
                        )
                        if (old != null) putExtra(EXTRA_SCREEN_CAPTURE_INTENT, old)
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

        tenkaRecruit = TenkaRecruit()

        notificationManager = systemService(context)!!
        windowManager = systemService(context)!!

        @SuppressLint("InflateParams")
        playView = LayoutInflater.from(context).inflate(R.layout.service_overlay, null)
        @SuppressLint("InflateParams")
        deleteView = LayoutInflater.from(context).inflate(R.layout.service_overlay_x, null)
        @SuppressLint("InflateParams")
        textView = LayoutInflater.from(context).inflate(R.layout.test, null)
        @SuppressLint("InflateParams")
        editView = LayoutInflater.from(context).inflate(R.layout.service_edit, null)
        @SuppressLint("InflateParams")
        popupView = LayoutInflater.from(context).inflate(R.layout.service_edit_popup, null)

        startForeground(notificationId, createRunningNotification(false))


        btnPlay = playView.findViewById(R.id.btnCamera)
        btnDelete = deleteView.findViewById(R.id.btnDelete)
        textBox = textView.findViewById(R.id.text)
        btnEdit = editView.findViewById(R.id.btnEdit)
        popup = popupView.findViewById(R.id.popup)
        editBox = popupView.findViewById(R.id.editBox)
        editFinishBtn = popupView.findViewById(R.id.editFinish)


        btnPlay.setOnClickListener(this)
        btnPlay.setOnTouchListener(this)
        textBox.setOnClickListener(this)
        btnEdit.setOnClickListener(this)
        editFinishBtn.setOnClickListener(this)


        playParam = initParam()
        deleteParam = initParam()
        textParam = initParam()
        editParam = initParam()
        popupParam = initPopupParam()

        val dm = resources.displayMetrics
        bigButtonSize = Pref.ipCameraButtonSize(App1.pref).toFloat().dp2px(dm)
        smallButtonSize = Pref.smallButton(App1.pref).toFloat().dp2px(dm)


        setDeletePos(dm)
        btnDelete.windowLayoutParams = deleteParam
        windowManager.addView(deleteView, deleteParam)

        setTextPos(dm)
        textBox.windowLayoutParams = textParam
        windowManager.addView(textView, textParam)

        setEditPos(dm)
        btnEdit.windowLayoutParams = editParam
        windowManager.addView(editView, editParam)

        setPopupPos(dm)
        popup.windowLayoutParams = editParam
        windowManager.addView(popupView, popupParam)

        loadButtonPosition()
        btnPlay.windowLayoutParams = playParam
        windowManager.addView(playView, playParam)

        btnDelete.setBackgroundResource(R.drawable.btn_bg_round_red)
        btnDelete.setImageResource(R.drawable.ic_delete)
        btnDelete.visibility = View.GONE

        btnEdit.setImageResource(R.drawable.btn_edit)
        btnEdit.visibility = View.GONE

        popup.visibility = View.GONE

        textBox.visibility = View.GONE
        textBoxOn = false
        showButtonAll()
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

    private fun initPopupParam() : WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            0,
            0,
            if (Build.VERSION.SDK_INT >= API_APPLICATION_OVERLAY) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            //WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.LEFT or Gravity.TOP
        }
    }

    private fun setPopupPos(dm : DisplayMetrics) {
        val size = Pref.ipTextBoxSize(App1.pref).toFloat().dp2px(dm)
        popupParam.width = dm.widthPixels
        popupParam.height = size
        popupParam.x = 0
        popupParam.y = size
    }

    private fun setEditPos(dm : DisplayMetrics) {
        editParam.width = smallButtonSize
        editParam.height = smallButtonSize
        editParam.x = clipInt(0, dm.widthPixels - smallButtonSize, dm.widthPixels - smallButtonSize - 20)
        editParam.y = clipInt(0, dm.heightPixels - smallButtonSize, bigButtonSize)
    }

    private fun setDeletePos(dm : DisplayMetrics) {
        deleteParam.width = bigButtonSize
        deleteParam.height = bigButtonSize
        deleteParam.x = clipInt(0, dm.widthPixels - bigButtonSize, (dm.widthPixels - bigButtonSize)/2)
        deleteParam.y = clipInt(0, dm.heightPixels - bigButtonSize, dm.heightPixels - bigButtonSize*3/2)
    }

    private fun setTextPos(dm : DisplayMetrics) {
        val size = Pref.ipTextBoxSize(App1.pref).toFloat().dp2px(dm)
        textParam.width = dm.widthPixels
        textParam.height = size
        textParam.x = 0
        textParam.y = 0
    }

    override fun onDestroy() {
        log.i("onDestroy start. stopReason=${getStopReason(this.javaClass)}")
        removeActiveService(this)
        isDestroyed = true
        windowManager.removeView(playView)
        windowManager.removeView(deleteView)
        windowManager.removeView(textView)
        windowManager.removeView(editView)
        stopForeground(true)

        if (getServices().isEmpty()) {
            log.i("onDestroy: captureJob join start")
            runBlocking {
                captureJob?.get()?.join()
            }
            log.i("onDestroy: captureJob join end")
            Capture.release("service.onDestroy")
        }

        showButtonAll()

        super.onDestroy()
        log.i("onDestroy end")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        reloadPosition()

        if (!isCapturing() && Capture.canCapture()) {
            try {
                Capture.updateMediaProjection("service.onConfigurationChanged")
            } catch (ex: Throwable) {
                log.eToast(this, ex, "updateMediaProjection failed.")
                stopWithReason("UpdateMediaProjectionFailedAtConfigurationChanged")
            }
        }
    }

    override fun onClick(v: View?) {
        if (v != null) {
            when (v.id) {
                R.id.btnCamera -> {
                    textBox.text = "  계산중..."
                    textBox.visibility = View.VISIBLE
                    textBoxOn = true
                    captureStart(3.0f)
                }
                R.id.text -> {
                    textBox.visibility = View.GONE
                    textBoxOn = false
                    btnEdit.visibility = View.GONE
                    popup.visibility = View.GONE
                }
                R.id.btnEdit -> {
                    popup.visibility = View.VISIBLE
                    val data = LocalDataManager.getString(applicationContext, txt)
                    if (data != "") editBox.setText(data)
                    else editBox.setText(txt)

                    btnEdit.clearFocus()
                    editBox.requestFocus()
                    if (editBox.hasFocus()) Log.i("focus", "editText가 포커스를 받음")
                    val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

                    Handler().postDelayed(Runnable(){imm.showSoftInput(editBox, 0)}, 100L)
                }
                R.id.editFinish -> {
                    popup.visibility = View.GONE
                    LocalDataManager.setString(applicationContext, txt, editBox.text.toString())
                }
            }
        }
    }

    //////////////////////////////////////////////

    private fun handleIntent(intent: Intent?) {
        val screenCaptureIntent = intent?.getParcelableExtraCompat<Intent>(EXTRA_SCREEN_CAPTURE_INTENT)
        if (screenCaptureIntent != null && Capture.screenCaptureIntent == null) {
            Capture.handleScreenCaptureIntentResult(this, Activity.RESULT_OK, screenCaptureIntent)
        }
    }

    private fun loadButtonPosition() {
        val dm = resources.displayMetrics
        playParam.width = bigButtonSize
        playParam.height = bigButtonSize
        playParam.x = clipInt(0, dm.widthPixels - bigButtonSize, dm.widthPixels - bigButtonSize * 3/2)
        playParam.y = clipInt(0, dm.heightPixels - bigButtonSize, dm.heightPixels * 3/5)
    }

    private fun reloadPosition() {
        loadButtonPosition()
        windowManager.updateViewLayout(playView, playParam)
    }

    //////////////////////////////////////////////

    private var hideByTouching = false

    fun showButton() {
        if (isDestroyed) return

        val isCapturing = Capture.isCapturing

        btnPlay.vg(!isCapturing)

        val hideByTouching = getServices().find { it.hideByTouching } != null

        if (hideByTouching) {
            btnPlay.background = null
            btnPlay.setImageDrawable(null)
            textBox.visibility = View.GONE
            btnEdit.visibility = View.GONE
            popup.visibility = View.GONE
        } else {
            btnPlay.setBackgroundResource(R.drawable.btn_bg_round)
            btnPlay.setImageResource(R.drawable.btn_play)
            if (textBoxOn) textBox.visibility = View.VISIBLE
        }
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
            hideByTouching = false
            showButtonAll()
        }
        playParam.x = clipInt(0, maxX, startLpX + deltaX.toInt())
        playParam.y = clipInt(0, maxY, startLpY + deltaY.toInt())

        windowManager.updateViewLayout(playView, playParam)
        return true
    }

    override fun onTouch(v: View?, ev: MotionEvent): Boolean {
        if (v?.id != R.id.btnCamera) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val dm = resources.displayMetrics
                startLpX = playParam.x
                startLpY = playParam.y
                startMotionX = ev.rawX
                startMotionY = ev.rawY
                draggingThreshold = resources.displayMetrics.density * 8f
                maxX = dm.widthPixels - bigButtonSize
                maxY = dm.heightPixels - bigButtonSize

                isDragging = false
                hideByTouching = true
                showButtonAll()

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

                val dist = bigButtonSize/2
                if (deleteParam.x - dist < playParam.x && playParam.x < deleteParam.x + dist &&
                    deleteParam.y - dist < playParam.y && playParam.y < deleteParam.y + dist) {
                    stopWithReason("StopButton")
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!updateDragging(ev, save = true)) {
                    hideByTouching = false
                    showButtonAll()
                }
                return true
            }
        }
        return false
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

    fun captureStop() {
        Capture.stopVideo()
    }

    private val uiHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetTextI18n")
    private fun setText() {
        uiHandler.post {
            if (txt != "") btnEdit.visibility = View.VISIBLE

            val data = LocalDataManager.getString(applicationContext, txt)
            if (data != "") {
                val tenkaResult = tenkaRecruit.getResult(data)
                textBox.text = " 인식 : $data\n 결과 : $tenkaResult"
            } else {
                val tenkaResult = tenkaRecruit.getResult(txt)
                textBox.text = " 인식 : $txt\n 결과 : $tenkaResult"
            }
        }
    }

    private fun captureStart(size: Float) {
        val timeClick = SystemClock.elapsedRealtime()

        // don't allow if service is not running
        if (isDestroyed) {
            log.e("captureStart(): service is already destroyed.")
            return
        }

        // don't allow other job is running.
        if (isCapturing()) {
            log.e("captureStart(): previous capture job is not complete.")
            return
        }

        log.w("captureStart 1")

        hideByTouching = false

        Capture.isCapturing = true
        showButtonAll()
        isVideoCaptureJob = isVideo
        captureJob = WeakReference(EmptyScope.launch(Dispatchers.IO) {
            for (nTry in 1..3) {
                log.w("captureJob try $nTry")
                try {
                    val captureResult = Capture.capture(
                        size,
                        context,
                        timeClick,
                        isVideo = false
                    )
                    txt = captureResult.text

                    log.w("captureJob captureResult=$captureResult")
                    break
                } catch (ex: Capture.ScreenCaptureIntentError) {

                    try {
                        log.e(ex, "captureJob failed. open activity…")
                        val state = suspendCoroutine<Capture.MediaProjectionState> { cont ->
                            ActScreenCaptureIntent.cont = cont
                            startActivity(
                                Intent(
                                    this@CaptureServiceBase,
                                    ActScreenCaptureIntent::class.java
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                }
                            )
                        }
                        if (state != Capture.MediaProjectionState.HasScreenCaptureIntent) {
                            log.d("resumed state is $state")
                            break
                        }
                        Capture.updateMediaProjection("recovery")
                        delay(500L)
                        continue
                    } catch (ex: Throwable) {
                        log.eToast(context, ex, "recovery failed.")
                        break
                    }

                } catch (ex: Throwable) {
                    log.eToast(context, ex, "capture failed.")
                    break
                }
            }
            setText()
        })
    }


    abstract fun createNotificationChannel(channelId: String)

    abstract fun arrangeNotification(
        builder: NotificationCompat.Builder,
        isRecording: Boolean
    ): IntArray

}

fun <T : CaptureServiceBase, R : Any?> T?.runOnService(
    context: Context,
    notificationId: Int? = null,
    block: T.() -> R
): R? = when (this) {
    null -> {
        CaptureServiceBase.logCompanion.eToast(context, false, "service not running.")
        if (notificationId != null) {
            systemService<NotificationManager>(context)?.cancel(notificationId)
        }
        null
    }
    else -> block.invoke(this)
}
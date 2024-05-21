package jp.juggler.screenshotbutton

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import android.widget.ImageButton
import android.widget.ListView
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
    private lateinit var popup: MyLinearLayout
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
    private var txt = ""
    private var time = ""
    private var textBoxOn = false
    private var editBtnOn = false

    private lateinit var tenkaRecruit: TenkaRecruit
    private lateinit var tenkaLeaderRecruit: TenkaLeaderRecruit

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
        tenkaLeaderRecruit = TenkaLeaderRecruit()

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startForeground(notificationId, createRunningNotification(false),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(notificationId, createRunningNotification(false))
        }


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
        windowManager.removeView(popupView)
        try {
            windowManager.removeView(ssrListView)
        } catch (_: Exception) {}
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

    @SuppressLint("SetTextI18n")
    override fun onClick(v: View?) {
        if (v != null) {
            when (v.id) {
                R.id.btnCamera -> {
                    textBox.text = "  계산중..."
                    textBox.visibility = View.VISIBLE
                    btnEdit.visibility = View.GONE
                    textBoxOn = true
                    editBtnOn = false
                    captureStart(1.0f)
                }
                R.id.text -> {
                    textBox.visibility = View.GONE
                    btnEdit.visibility = View.GONE
                    popup.visibility = View.GONE
                    textBoxOn = false
                    editBtnOn = false
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
                    val editBoxText = editBox.text.toString()
                    LocalDataManager.setString(applicationContext, txt, editBoxText)

                    textBox.text = "  계산중..."
                    val editResult = tenkaRecruit.getResult(editBoxText).joinToString("")
                    textBox.text = " 인식 : $editBoxText\n 결과 : $editResult"
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
            btnPlay.setImageResource(R.drawable.btn_search)
            if (textBoxOn) textBox.visibility = View.VISIBLE
            if (editBtnOn) btnEdit.visibility = View.VISIBLE
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

                val dist = bigButtonSize * 2 / 3
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

    private val uiHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetTextI18n")
    private fun setText() {
        uiHandler.post {
            if (txt != "" && textBoxOn) {
                editBtnOn = true
                btnEdit.visibility = View.VISIBLE
            }

            var data = LocalDataManager.getString(applicationContext, txt)
            if (data == "") data = txt

            log.i(data)
            log.i(time)
            if (data.split(" ").size != 5) {
                log.i("인식했을때 5개가 아님")
                textBox.text = " 인식 : $data\n 결과 : 인식 오류"
            } else {
                val tmpArr = data.split(" ")
                val inputTags = arrayOf(tmpArr[0], tmpArr[2], tmpArr[4], tmpArr[1], tmpArr[3]).joinToString(" ")
                val tenkaResult = tenkaRecruit.getResult(inputTags)

                textBox.text = " 인식 : $inputTags\n 결과 : ${tenkaResult.joinToString("")}"

                if (tenkaResult[0] == "SSR 리더") showSsrList(inputTags)
                else {
                    // autoTouch 옵션이 켜져 있다면 autoTouch 시작
                    val isAutoTouch = LocalDataManager.getInt(applicationContext, "AUTO_TOUCH")
                    if (isAutoTouch == 1) startTagAutoTouch(time, inputTags, tenkaResult[1])
                }
            }
        }
    }

    private val handler = Handler()
    private fun startTagAutoTouch(time: String, input: String, result: String) {
        log.i("start AutoTouch")

        var timeInt = time.toIntOrNull()
        if (timeInt == null || timeInt !in 1..9 || input == "") return
        if ("인식 오류" == result) return
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels

        if (autoClickService == null) Log.i("alert", "autoClickService is null")
        log.i("start time click")
        // 시간 클릭
        val timePos = arrayOf(arrayOf(w * 0.25, h * 0.19), arrayOf(w * 0.25, h * 0.28))
        var dir = 0
        if (9 - timeInt > 4) dir = 1
        else timeInt = 9 - timeInt

        var currentTimeClick = 0
        @SuppressLint("SetTextI18n")
        fun clickTime() {
            if (currentTimeClick < timeInt) {
                autoClickService?.click(timePos[dir][0].toInt(), timePos[dir][1].toInt())
                currentTimeClick++
                handler.postDelayed({ clickTime() }, 200L)
            } else {
                clickTag(w, h, input, result)
            }
        }
        clickTime()
    }
    private fun clickTag(w: Int, h: Int, input: String, result: String) {
        log.i("start tag click")
        // 태그 클릭
        val tagPos = arrayOf(
            arrayOf(w * 0.25, h * 0.38), arrayOf(w * 0.5, h * 0.38), arrayOf(w * 0.75, h * 0.38),
            arrayOf(w * 0.25, h * 0.44), arrayOf(w * 0.5, h * 0.44)
        )
        val inArr = input.split(" ")
        val outArr = result.split(" ")
        val clickNum = mutableListOf<Int>()
        for(i in 0..4) if (outArr.contains(inArr[i])) clickNum.add(i)

        var currentTagClick = 0
        fun clickTag() {
            if (currentTagClick < clickNum.size) {
                val index = clickNum[currentTagClick]
                autoClickService?.click(tagPos[index][0].toInt(), tagPos[index][1].toInt())
                currentTagClick++
                handler.postDelayed({ clickTag() }, 200L)
            }
        }
        clickTag()
    }
//    private fun startTagAutoTouch(time: String, input: String, result: String) {
//        log.i("start AutoTouch")
//        if (time == "" || input == "") return
//        if ("인식 오류" == result) return
//        val dm = resources.displayMetrics
//        val w = dm.widthPixels
//        val h = dm.heightPixels
//
//        if (autoClickService == null) {
//            Log.i("alert", "autoClickService is null")
//            testText("autoClickService is null")
//        }
//        log.i("start time click")
//        // 시간 클릭
//        var timeInt = Integer.parseInt(time)
//        val timePos = arrayOf(arrayOf(w * 0.25, h * 0.19), arrayOf(w * 0.25, h * 0.28))
//        var dir = 0
//        if (9 - timeInt > 4) dir = 1
//        else timeInt = 9 - timeInt
//
//        for(i in 1..timeInt) {
//            autoClickService?.click(timePos[dir][0].toInt(), timePos[dir][1].toInt())
//            Thread.sleep(200L)
//        }
//        log.i("start tag click")
//        // 태그 클릭
//        val tagPos = arrayOf(
//            arrayOf(w * 0.25, h * 0.38), arrayOf(w * 0.5, h * 0.38), arrayOf(w * 0.75, h * 0.38),
//            arrayOf(w * 0.25, h * 0.44), arrayOf(w * 0.5, h * 0.44)
//        )
//        val inArr = input.split(" ")
//        val outArr = result.split(" ")
//        val clickNum = mutableListOf<Int>()
//        for(i in 0..4) if (outArr.contains(inArr[i])) clickNum.add(i)
//        for(i in clickNum) {
//            autoClickService?.click(tagPos[i][0].toInt(), tagPos[i][1].toInt())
//            Thread.sleep(200L)
//        }
//    }

    private lateinit var ssrListView: View
    private lateinit var layout: MyLinearLayout
    private lateinit var listExitBtn: ImageButton
    private lateinit var listView: ListView
    private lateinit var listViewParam: WindowManager.LayoutParams
    private fun showSsrList(tags: String) {
        @SuppressLint("InflateParams")
        val ssrListView = LayoutInflater.from(context).inflate(R.layout.listview, null)
        val ssrList = tenkaLeaderRecruit.getResult(tags)

        layout = ssrListView.findViewById(R.id.ssr_list_layout)
        listExitBtn = ssrListView.findViewById(R.id.btn_exit)
        listView = ssrListView.findViewById(R.id.ssr_list)
        listView.adapter = SSRListAdapter(context, ssrList)

        listViewParam = initParam()
        val dm = resources.displayMetrics
        val size = Pref.ipTextBoxSize(App1.pref).toFloat().dp2px(dm)
        listViewParam.width = (dm.widthPixels * 9) / 10
        listViewParam.height = ((dm.heightPixels - size) * 9) / 10
        listViewParam.x = dm.widthPixels / 20
        listViewParam.y = (dm.heightPixels - size) / 20 + size

        listExitBtn.background = null
        listExitBtn.setImageResource(R.drawable.ic_close_white)
        listExitBtn.setOnClickListener {
            try {
                windowManager.removeView(ssrListView)
            } catch (_: Exception) {}
        }

        layout.windowLayoutParams = listViewParam
        windowManager.addView(ssrListView, listViewParam)
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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    log.i("this is api 34")
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
                    }
                    Capture.updateMediaProjection("recovery")
                }
                val captureResult = Capture.capture(
                    size,
                    context,
                    timeClick,
                    isVideo = false
                )
                txt = captureResult.text
                time = captureResult.time
                log.w("captureJob captureResult=$captureResult")
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
                    }
                    Capture.updateMediaProjection("recovery")
                    delay(500L)
                } catch (ex: Throwable) {
                    log.eToast(context, ex, "recovery failed.")
                }

            } catch (ex: Throwable) {
                log.eToast(context, ex, "capture failed.")
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
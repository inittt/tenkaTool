package jp.juggler.screenshotbutton

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import jp.juggler.screenshotbutton.databinding.ActMainBinding
import jp.juggler.util.*
import java.lang.ref.WeakReference

class ActMain : AppCompatActivity() {

    companion object {
        private val log = LogCategory("${App1.tagPrefix}/ActMain")

        private var refActivity: WeakReference<ActMain>? = null

        fun getActivity() = refActivity?.get()
    }

    private val views by lazy {
        ActMainBinding.inflate(layoutInflater)
    }

    private var lastDialog: WeakReference<Dialog>? = null

    private var timeStartButtonTappedStill = 0L
    private var timeStartButtonTappedVideo = 0L
    private var timeStartButtonTappedMacro = 0L

    private val arOverlay = ActivityResultHandler {
        mayContinueDispatchRecruit(handleOverlayResult())
    }

    private val arScreenCapture = ActivityResultHandler { r ->
        mayContinueDispatchRecruit(Capture.handleScreenCaptureIntentResult(this, r.resultCode, r.data))
    }
    private val arMacro = ActivityResultHandler { r ->
        mayContinueDispatchMacro(Macro.handleMacroIntentResult(this, r.resultCode, r.data))
    }

    private val arDocumentTree = ActivityResultHandler { r ->
        mayContinueDispatchRecruit(handleSaveTreeUriResult(r.resultCode, r.data))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        arOverlay.register(this)
        arScreenCapture.register(this)
        arMacro.register(this)
        arDocumentTree.register(this)

        // Load language data
        LocalDataManager.LANG = LocalDataManager.getString(applicationContext, "LANG")

        App1.prepareAppState(this)
        log.d("onCreate savedInstanceState=$savedInstanceState")
        refActivity = WeakReference(this)
        super.onCreate(savedInstanceState)
        initUI()
    }

    override fun onStart() {
        log.d("onStart")
        super.onStart()
    }

    private fun startRecruitTool() {
        dispatchRecruit()
        when (val service = CaptureServiceStill.getService()) {
            null -> {
                timeStartButtonTappedStill = SystemClock.elapsedRealtime()
                dispatchRecruit()
            }
            else -> {
                service.stopWithReason("StopButton")
            }
        }
    }

    private fun startMacroTool() {
        dispatchMacro()
        when (val service = MacroService.getService()) {
            null -> {
                timeStartButtonTappedMacro = SystemClock.elapsedRealtime()
                dispatchMacro()
            }
            else -> {
                service.stopWithReason("StopButton")
            }
        }
    }

    /////////////////////////////////////////

    private fun initUI() {
        setContentView(R.layout.act_main)

        // load ads
        val adView = findViewById<AdView>(R.id.adView)
        val adRequest = AdRequest.Builder().build()

        // 광고 로드 후에 실행될 콜백 설정
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                // 광고가 로드된 후에 버튼을 보이도록 설정
                showButtons()
            }
            override fun onAdFailedToLoad(adError: LoadAdError) {
                // 광고 로드에 실패한 경우에도 버튼을 보이도록 설정
                showButtons()
                log.e("Failed to load ad: ${adError.message}")
            }
        }

        // 광고 로드 요청
        adView.loadAd(adRequest)
    }
    private fun showButtons() {
        val appLogo: ImageView = findViewById(R.id.app_logo)
        appLogo.visibility = View.GONE

        // button act
        val title: ImageView = findViewById(R.id.titleImage)
        val recruitBtn: Button = findViewById(R.id.btn_recruit)
        val macroBtn: Button = findViewById(R.id.btn_macro)
        val optionBtn: Button = findViewById(R.id.btn_option)
        val exitBtn: Button = findViewById(R.id.btn_exit)

        optionBtn.setOnClickListener {
            val nextIntent = Intent(this, ActOption::class.java)
            startActivity(nextIntent)
        }
        exitBtn.setOnClickListener {
            finish()
            //finishAndRemoveTask()
        }
        recruitBtn.setOnClickListener { startRecruitTool() }
        macroBtn.setOnClickListener { startMacroTool() }

        // 광고가 로드되었으므로 버튼을 보이도록 설정
        title.visibility = View.VISIBLE
        recruitBtn.visibility = View.VISIBLE
        macroBtn.visibility = View.VISIBLE
        optionBtn.visibility = View.VISIBLE
        exitBtn.visibility = View.VISIBLE
    }

    private fun mayContinueDispatchRecruit(r: Boolean) {
        if (r) {
            dispatchRecruit()
        } else {
            timeStartButtonTappedStill = 0L
            timeStartButtonTappedVideo = 0L
        }
    }
    private fun mayContinueDispatchMacro(r: Boolean) {
        if (r) {
            dispatchMacro()
        } else {
            timeStartButtonTappedMacro = 0L
        }
    }

    private fun dispatchRecruit() {
        log.d("dispatch recruit")
        if (!prepareOverlay()) return
        if (LocalDataManager.getInt(applicationContext, "AUTO_TOUCH") == 1) {
            if (!prepareAccessibility()) return
        }

        if (timeStartButtonTappedStill > 0L) {

            if (!Capture.prepareScreenCaptureIntent(arScreenCapture)) return

            timeStartButtonTappedStill = 0L
            ContextCompat.startForegroundService(
                this,
                Intent(this, CaptureServiceStill::class.java).apply {
                    Capture.screenCaptureIntent?.let {
                        putExtra(CaptureServiceBase.EXTRA_SCREEN_CAPTURE_INTENT, it)
                    }
                }
            )
            finish()
            //finishAndRemoveTask()
        }
    }

    private fun dispatchMacro() {
        log.d("dispatch macro")
        if (!prepareOverlay()) return
        if (!prepareAccessibility()) return

        if (timeStartButtonTappedMacro > 0L) {

            timeStartButtonTappedMacro = 0L

            ContextCompat.startForegroundService(
                this,
                Intent(this, MacroService::class.java).apply {
                    Macro.macroIntent?.let {
                        putExtra(MacroServiceBase.EXTRA_MACRO_INTENT, it)
                    }
                }
            )
            finish()
            //finishAndRemoveTask()
        }
    }

    ///////////////////////////////////////////////////////

    private fun handleSaveTreeUriResult(resultCode: Int, data: Intent?): Boolean {
        try {
            if (resultCode == RESULT_OK) {
                val uri = data?.data ?: error("missing document tree URI")
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                App1.pref.edit().put(Pref.spSaveTreeUri, uri.toString()).apply()
                return true
            }
        } catch (ex: Throwable) {
            log.eToast(this, ex, "takePersistableUriPermission failed.")
        }
        return false
    }

    /////////////////////////////////////////////////////////////////

    @SuppressLint("InlinedApi")
    private fun prepareOverlay(): Boolean {
        if (canDrawOverlaysCompat(this)) return true

        return AlertDialog.Builder(this)
            .setMessage(R.string.please_allow_overlay_permission)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                arOverlay.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            .showEx()
    }

    @SuppressLint("InlinedApi")
    private fun prepareAccessibility(): Boolean {
        if (isAccessibilityServiceEnable(this)) return true

        return AlertDialog.Builder(this)
            .setMessage(R.string.please_allow_accessibility_permission)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val myIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                myIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(myIntent)

            }
            .showEx()
    }

    private fun handleOverlayResult(): Boolean {
        return canDrawOverlaysCompat(this)
    }

    ///////////////////////////////////////////////////


    private fun AlertDialog.Builder.showEx(): Boolean {
        if (lastDialog?.get()?.isShowing == true) {
            log.w("dialog is already showing.")
        } else {
            lastDialog = WeakReference(this.create().apply { show() })
        }
        return false
    }
}

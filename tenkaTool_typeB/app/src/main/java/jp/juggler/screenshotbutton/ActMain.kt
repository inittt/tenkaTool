package jp.juggler.screenshotbutton

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    private val arOverlay = ActivityResultHandler {
        mayContinueDispatch(handleOverlayResult())
    }

    private val arScreenCapture = ActivityResultHandler { r ->
        mayContinueDispatch(Capture.handleScreenCaptureIntentResult(this, r.resultCode, r.data))
    }

    private val arDocumentTree = ActivityResultHandler { r ->
        mayContinueDispatch(handleSaveTreeUriResult(r.resultCode, r.data))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        arOverlay.register(this)
        arScreenCapture.register(this)
        arDocumentTree.register(this)
        App1.prepareAppState(this)
        log.d("onCreate savedInstanceState=$savedInstanceState")
        refActivity = WeakReference(this)

        super.onCreate(savedInstanceState)
        //initUI()
    }

    override fun onStart() {
        log.d("onStart")
        super.onStart()
        dispatch()
        when (val service = CaptureServiceStill.getService()) {
            null -> {
                timeStartButtonTappedStill = SystemClock.elapsedRealtime()
                dispatch()
            }
            else -> {
                service.stopWithReason("StopButton")
            }
        }
    }

    /////////////////////////////////////////
    private fun mayContinueDispatch(r: Boolean) {
        if (r) {
            dispatch()
        } else {
            timeStartButtonTappedStill = 0L
            timeStartButtonTappedVideo = 0L
        }
    }

    private fun dispatch() {
        log.d("dispatch")

        if (!prepareOverlay()) return
        if (!prepareAccessibility()) return

        //if (!prepareSaveTreeUri()) return

        if (timeStartButtonTappedStill > 0L) {

            if (!Capture.prepareScreenCaptureIntent(arScreenCapture)) return

            timeStartButtonTappedStill = 0L
            val intent = Intent(this, CaptureServiceStill::class.java).apply {
                Capture.screenCaptureIntent?.let {
                    putExtra(CaptureServiceBase.EXTRA_SCREEN_CAPTURE_INTENT, it)
                }
            }
            ContextCompat.startForegroundService(this, intent)
            finishAndRemoveTask()
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

    private fun handleAccessibilityResult(): Boolean {
        return isAccessibilityServiceEnable(this)
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

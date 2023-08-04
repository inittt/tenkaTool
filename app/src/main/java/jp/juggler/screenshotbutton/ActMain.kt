package jp.juggler.screenshotbutton

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import jp.juggler.screenshotbutton.databinding.ActMainBinding
import jp.juggler.util.*
import java.lang.ref.WeakReference
import kotlin.math.max

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

    private var videoCaptureEnabled = false

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

    // ActivityResultの処理結果によってdispatchを再実行する
    private fun mayContinueDispatch(r: Boolean) {
        if (r) {
            dispatch()
        } else {
            timeStartButtonTappedStill = 0L
            timeStartButtonTappedVideo = 0L
        }
    }

    // 権限のチェックと取得インタラクションの開始
    // 画面表示時や撮影ボタンの表示開始時に呼ばれる
    private fun dispatch() {
        log.d("dispatch")

        if (!prepareOverlay()) return

        //if (!prepareSaveTreeUri()) return

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
            finishAndRemoveTask()
            //finish()
        }
    }

    ///////////////////////////////////////////////////////
    // 保存フォルダの選択と書き込み権限

    private fun openSaveTreeUriChooser() {
        arDocumentTree.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                if (Build.VERSION.SDK_INT >= API_EXTRA_INITIAL_URI) {
                    val saveTreeUri = Pref.spSaveTreeUri(App1.pref)
                    if (saveTreeUri.isNotEmpty()) {
                        putExtra(
                            DocumentsContract.EXTRA_INITIAL_URI,
                            saveTreeUri
                        )
                    }
                }
            }
        )
    }

    private fun prepareSaveTreeUri(): Boolean {
        val treeUri = Pref.spSaveTreeUri(App1.pref).toUriOrNull()

        if (treeUri != null) {
            if (!contentResolver.persistedUriPermissions.any { it.uri == treeUri }) {
                log.eToast(this, true, "missing access permission $treeUri")
            } else {
                try {
                    // pathの検証。例外を出す
                    pathFromDocumentUriOrThrow(this, treeUri)
                    return true
                } catch (ex: Throwable) {
                    log.eToast(this, ex, "can't use this folder.")
                }
            }
        }

        AlertDialog.Builder(this)
            .setMessage(R.string.please_select_save_folder)
            .setPositiveButton(R.string.ok)
            { _, _ ->
                openSaveTreeUriChooser()
            }
            .setNegativeButton(R.string.cancel, null)
            .showEx()

        return false
    }

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
    // オーバーレイ表示の権限

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

    private fun handleOverlayResult(): Boolean {
        // 設定画面から戻るボタンなどで復帰するため、 resultCode が RESULT_OK になることはない
        return canDrawOverlaysCompat(this)
    }

    ///////////////////////////////////////////////////
    // ダイアログの多重表示を防止する


    private fun AlertDialog.Builder.showEx(): Boolean {
        if (lastDialog?.get()?.isShowing == true) {
            log.w("dialog is already showing.")
        } else {
            lastDialog = WeakReference(this.create().apply { show() })
        }
        return false
    }
}

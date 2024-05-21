package jp.juggler.screenshotbutton

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.*
import android.view.WindowManager
import jp.juggler.util.*


object Macro {
    private val log = LogCategory("${App1.tagPrefix}/Macro")

    private val handlerThread: HandlerThread = HandlerThread("Capture.handler").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private lateinit var windowManager: WindowManager

    var macroIntent: Intent? = null

    fun onInitialize(context: Context) {
        log.d("onInitialize")
        windowManager = systemService(context)!!
    }

    // mediaProjection と screenCaptureIntentを解放する
    fun release(caller: String): Boolean {
        log.d("release. caller=$caller")

        macroIntent = null

        return false
    }

    fun handleMacroIntentResult(
        context: Context,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        log.d("handleMacroIntentResult")
        return when {
            resultCode != Activity.RESULT_OK -> {
                log.eToast(context, false, "permission not granted.")
                release("handleMacroIntentResult: permission not granted.")
            }

            data == null -> {
                log.eToast(context, false, "result data is null.")
                release("handleMacroIntentResult: intent is null.")
            }

            else -> {
                log.i("screenCaptureIntent set!")
                true
            }
        }
    }

    class MacroIntentError(msg: String) : IllegalStateException(msg)

    data class MacroResult(
        val text: String
    )
}
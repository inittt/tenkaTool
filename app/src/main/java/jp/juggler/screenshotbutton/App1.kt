package jp.juggler.screenshotbutton

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityManager
import jp.juggler.util.LogCategory
import android.provider.Settings
import android.app.AlertDialog;


class App1 : Application() {
    companion object {
        const val tagPrefix = "ScreenShotButton"

        lateinit var pref: SharedPreferences

        private var isPrepared = false

        fun prepareAppState(contextArg: Context) {
            if (!isPrepared) {
                val context = contextArg.applicationContext
                isPrepared = true
                pref = Pref.pref(context)
                LogCategory.onInitialize(context)
                Capture.onInitialize(context)

            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prepareAppState(applicationContext)
    }
}

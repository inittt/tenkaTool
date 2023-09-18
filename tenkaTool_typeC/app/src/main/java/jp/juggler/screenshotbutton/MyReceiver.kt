package jp.juggler.screenshotbutton

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.juggler.util.LogCategory

class MyReceiver : BroadcastReceiver() {
    companion object {
        private val log = LogCategory("${App1.tagPrefix}/MyReceiver")

        const val ACTION_RUNNING_DELETE_STILL = "running_delete_still"
    }

    override fun onReceive(context: Context, data: Intent?) {
        App1.prepareAppState(context)
        val action = data?.action
        log.i("onReceive $action")
        when (data?.action) {
            ACTION_RUNNING_DELETE_STILL ->
                CaptureServiceStill.getService()
                    .runOnService(context, NOTIFICATION_ID_RUNNING_STILL) {
                        stopWithReason("NotificationDeleteAction")
                    }
        }
    }
}

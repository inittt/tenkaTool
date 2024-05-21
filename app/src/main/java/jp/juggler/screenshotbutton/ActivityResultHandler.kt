package jp.juggler.screenshotbutton

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class ActivityResultHandler(val handleResult: (ActivityResult) -> Unit) {
    private var launcher: ActivityResultLauncher<Intent>? = null

    fun register(activity: AppCompatActivity) {
        launcher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { handleResult(it) }
    }

    fun register(fragment: Fragment) {
        launcher = fragment.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { handleResult(it) }
    }

    fun launch(intent: Intent) {
        (launcher ?: error("ActivityResultHandler: not registered to activity!"))
            .launch(intent)
    }
}

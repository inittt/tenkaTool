package jp.juggler.screenshotbutton

import android.annotation.SuppressLint
import android.content.Intent
import android.opengl.Visibility
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import jp.juggler.util.*
import java.lang.ref.WeakReference

class ActOption : AppCompatActivity() {
    companion object {
        private val log = LogCategory("${App1.tagPrefix}/ActMain")

        private var refActivity: WeakReference<ActOption>? = null

        fun getActivity() = refActivity?.get()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initUI()
    }
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun initUI() {
        setContentView(R.layout.act_option)

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
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun showButtons() {
        // button act
        val textView: TextView = findViewById(R.id.textView)
        val switchAuto: Switch = findViewById(R.id.switch_autotouch)
        val switchEmulator: Switch = findViewById(R.id.switch_emulator)//exit button
        val exitBtn: ImageButton = findViewById(R.id.btn_exit)

        exitBtn.setImageResource(R.drawable.ic_close)
        if (LocalDataManager.getInt(applicationContext, "AUTO_TOUCH") == 1) switchAuto.isChecked = true
        if (LocalDataManager.getInt(applicationContext, "IS_EMULATOR") == 1) switchEmulator.isChecked = true

        exitBtn.setOnClickListener{
            val nextIntent = Intent(this, ActMain::class.java)
            startActivity(nextIntent)
        }
        switchAuto.setOnClickListener {
            if (switchAuto.isChecked) LocalDataManager.setInt(applicationContext, "AUTO_TOUCH", 1)
            else LocalDataManager.setInt(applicationContext, "AUTO_TOUCH", 0)
        }
        switchEmulator.setOnClickListener {
            if (switchEmulator.isChecked) LocalDataManager.setInt(applicationContext, "IS_EMULATOR", 1)
            else LocalDataManager.setInt(applicationContext, "IS_EMULATOR", 0)
        }

        textView.visibility = View.VISIBLE
        exitBtn.visibility = View.VISIBLE
        switchAuto.visibility = View.VISIBLE
        switchEmulator.visibility = View.VISIBLE
    }
}

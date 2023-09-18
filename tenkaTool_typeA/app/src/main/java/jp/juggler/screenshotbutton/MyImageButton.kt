package jp.juggler.screenshotbutton

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.WindowManager
import android.widget.ImageButton

@SuppressLint("AppCompatCustomView")
class MyImageButton : ImageButton {
    var windowLayoutParams : WindowManager.LayoutParams? = null
    constructor(context: Context) : super(context, null)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
}

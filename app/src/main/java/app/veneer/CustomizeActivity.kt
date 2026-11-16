package app.veneer

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.veneer.ui.customize.CustomizeRoot

/** Every customization screen. Also the app's entry in the app list. */
class CustomizeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val section = intent.getStringExtra(EXTRA_SECTION)
        setContent { CustomizeRoot(section, onFinish = ::finish) }
    }

    companion object {
        const val EXTRA_SECTION = "section"
    }
}

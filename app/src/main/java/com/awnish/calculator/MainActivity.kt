package com.awnish.calculator

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.awnish.calculator.ui.AwnishApp

class MainActivity : FragmentActivity() {
    private val sharedUris = mutableStateListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedUris.addAll(extractSharedUris(intent))
        enableEdgeToEdge()
        configureSystemBars()

        setContent {
            com.awnish.calculator.ui.theme.AwnishTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AwnishApp(
                        activity = this,
                        incomingUris = sharedUris.toList(),
                        launcherIconVisible = isLauncherIconVisible(),
                        onSetLauncherIconVisible = { visible -> setLauncherIconVisible(visible) },
                        onIncomingUrisConsumed = { sharedUris.clear() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incoming = extractSharedUris(intent)
        if (incoming.isNotEmpty()) {
            sharedUris.clear()
            sharedUris.addAll(incoming)
        }
    }

    private fun extractSharedUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::listOf) ?: emptyList()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList() ?: emptyList()
            }
            else -> emptyList()
        }
    }

    private fun isLauncherIconVisible(): Boolean {
        val component = ComponentName(this, MainActivity::class.java)
        return packageManager.getComponentEnabledSetting(component) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun setLauncherIconVisible(visible: Boolean) {
        val component = ComponentName(this, MainActivity::class.java)
        packageManager.setComponentEnabledSetting(
            component,
            if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        if (!visible) {
            finishAndRemoveTask()
        }
    }

    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = AndroidColor.rgb(250, 247, 248)
        window.navigationBarColor = AndroidColor.rgb(250, 247, 248)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}

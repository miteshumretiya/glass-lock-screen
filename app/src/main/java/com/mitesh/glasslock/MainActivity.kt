package com.mitesh.glasslock

import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.mitesh.glasslock.ui.SetupScreen

/** Setup screen: permissions, wallpaper, options and a preview. */
class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { SetupScreen(resumeTick.intValue) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        if (Prefs(this).enabled && Settings.canDrawOverlays(this)) LockService.start(this)
    }
}

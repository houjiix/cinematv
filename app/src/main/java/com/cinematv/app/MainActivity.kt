package com.cinematv.app

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cinematv.app.ui.navigation.CinemaTvRootApp
import com.cinematv.app.ui.theme.CinemaTvTheme
import com.cinematv.app.ui.viewmodel.CinemaViewModel
import com.cinematv.app.ui.viewmodel.CinemaViewModelFactory

/**
 * Single adaptive entry activity supporting Android Phones, Tablets, Android TV, Google TV, and TV boxes.
 * Automatically detects Television UI mode / Leanback feature and presents the tailored interface.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTvDevice = detectTelevisionDevice(this)
        if (!isTvDevice) {
            enableEdgeToEdge()
        }

        val container = (application as CinemaTvApplication).container

        setContent {
            val viewModel: CinemaViewModel = viewModel(factory = CinemaViewModelFactory(container))
            val settings by viewModel.settings.collectAsState()

            CinemaTvTheme(darkTheme = settings.darkTheme) {
                CinemaTvRootApp(
                    isTvDevice = isTvDevice,
                    viewModel = viewModel
                )
            }
        }
    }

    companion object {
        fun detectTelevisionDevice(context: Context): Boolean {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            return isTvMode || hasLeanback
        }
    }
}

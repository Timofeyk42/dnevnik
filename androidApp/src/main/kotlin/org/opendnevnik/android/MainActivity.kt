package org.opendnevnik.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import org.opendnevnik.shared.planner.ThemeMode
import org.opendnevnik.android.presentation.DiaryViewModel
import org.opendnevnik.android.ui.DiaryApp
import org.opendnevnik.android.ui.DiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: DiaryViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            val preferences = state.planner.preferences
            DiaryTheme(darkTheme = when (preferences.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }, dynamicColor = preferences.dynamicColors) {
                DiaryApp(model)
            }
        }
    }
}

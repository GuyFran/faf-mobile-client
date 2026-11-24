package com.faforever.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.faforever.mobile.navigation.FafNavHost
import com.faforever.mobile.ui.state.LocalUiState
import com.faforever.mobile.ui.state.UiStatePrefs
import com.faforever.mobile.ui.theme.FafTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** UI state store; injected (and loaded) before the first frame so screens restore without a jump. */
    @Inject lateinit var uiState: UiStatePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalUiState provides uiState) {
                FafTheme {
                    FafNavHost()
                }
            }
        }
    }
}

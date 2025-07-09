package acidicoala.koalageddon.uplay.ui

import acidicoala.koalageddon.core.ui.composable.PlatformScreen
import androidx.compose.runtime.Composable
import acidicoala.koalageddon.uplay.ui.UplayGameScreen

@Composable
fun UplayScreen() {
    PlatformScreen(
        storeTab = { UplayStoreScreen() },
        gameTab = { UplayGameScreen() }
    )
} 
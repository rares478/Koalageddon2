package acidicoala.koalageddon.uplay.ui

import acidicoala.koalageddon.core.model.KoalaTool.SleepAPI.Config
import acidicoala.koalageddon.core.ui.composable.SwitchOption
import acidicoala.koalageddon.core.ui.composition.LocalStrings
import androidx.compose.foundation.layout.padding
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UplayConfigurationStore(
    config: Config,
    onConfigChange: (Config) -> Unit
) {
    val strings = LocalStrings.current

    // Logging switch
    SwitchOption(
        label = strings.logging,
        checked = config.general.logging,
        onCheckedChange = { onConfigChange(config.copy(general = config.general.copy(logging = it))) }
    )

    // Language text field
    OutlinedTextField(
        value = config.r1.lang,
        onValueChange = { onConfigChange(config.copy(r1 = config.r1.copy(lang = it))) },
        label = { Text(strings.language) },
        singleLine = true,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
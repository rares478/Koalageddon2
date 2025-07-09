package acidicoala.koalageddon.uplay.ui

import acidicoala.koalageddon.core.model.KoalaTool.SleepAPI.Config
import acidicoala.koalageddon.core.model.KoalaTool.SleepAPI.Dlc
import acidicoala.koalageddon.core.model.KoalaTool.SleepAPI.Item
import acidicoala.koalageddon.core.ui.composable.SwitchOption
import acidicoala.koalageddon.core.ui.composable.IntListOption
import acidicoala.koalageddon.core.ui.composable.DefaultSpacer
import acidicoala.koalageddon.core.ui.composition.LocalStrings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UplayConfigurationGame(
    config: Config,
    onConfigChange: (Config) -> Unit
) {
    val strings = LocalStrings.current

    Column {
        // Logging
        SwitchOption(
            label = strings.logging,
            checked = config.general.logging,
            onCheckedChange = { onConfigChange(config.copy(general = config.general.copy(logging = it))) }
        )
        DefaultSpacer()

        // R1: Language
        OutlinedTextField(
            value = config.r1.lang,
            onValueChange = { onConfigChange(config.copy(r1 = config.r1.copy(lang = it))) },
            label = { Text(strings.language + " (r1)") },
            singleLine = true,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        // R1: Hook loader
        SwitchOption(
            label = "Hook Loader (r1)",
            checked = config.r1.hook_loader,
            onCheckedChange = { onConfigChange(config.copy(r1 = config.r1.copy(hook_loader = it))) }
        )
        // R1: Blacklist
        OutlinedTextField(
            value = config.r1.blacklist.joinToString(", "),
            onValueChange = { onConfigChange(config.copy(r1 = config.r1.copy(blacklist = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }))) },
            label = { Text("Blacklist (r1)") },
            singleLine = true,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        DefaultSpacer()

        // R2: Language
        OutlinedTextField(
            value = config.r2.lang,
            onValueChange = { onConfigChange(config.copy(r2 = config.r2.copy(lang = it))) },
            label = { Text(strings.language + " (r2)") },
            singleLine = true,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        // R2: Hook loader
        SwitchOption(
            label = "Hook Loader (r2)",
            checked = config.r2.hook_loader,
            onCheckedChange = { onConfigChange(config.copy(r2 = config.r2.copy(hook_loader = it))) }
        )
        // R2: Auto fetch
        SwitchOption(
            label = "Auto Fetch (r2)",
            checked = config.r2.auto_fetch,
            onCheckedChange = { onConfigChange(config.copy(r2 = config.r2.copy(auto_fetch = it))) }
        )
        // R2: Blacklist
        OutlinedTextField(
            value = config.r2.blacklist.joinToString(", "),
            onValueChange = { onConfigChange(config.copy(r2 = config.r2.copy(blacklist = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }))) },
            label = { Text("Blacklist (r2)") },
            singleLine = true,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        DefaultSpacer()

        // R2: DLCs
        Text("DLCs (r2)")
        config.r2.dlcs.forEachIndexed { index, dlc ->
            Column(Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                OutlinedTextField(
                    value = dlc.ProductID.toString(),
                    onValueChange = { newId ->
                        val updated = config.r2.dlcs.toMutableList()
                        updated[index] = dlc.copy(ProductID = newId.toIntOrNull() ?: 0)
                        onConfigChange(config.copy(r2 = config.r2.copy(dlcs = updated)))
                    },
                    label = { Text("ProductID") },
                    singleLine = true,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = dlc.name,
                    onValueChange = { newName ->
                        val updated = config.r2.dlcs.toMutableList()
                        updated[index] = dlc.copy(name = newName)
                        onConfigChange(config.copy(r2 = config.r2.copy(dlcs = updated)))
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                androidx.compose.material.Button(
                    onClick = {
                        val updated = config.r2.dlcs.toMutableList().apply { removeAt(index) }
                        onConfigChange(config.copy(r2 = config.r2.copy(dlcs = updated)))
                    },
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text("Remove")
                }
            }
        }
        androidx.compose.material.Button(
            onClick = {
                val updated = config.r2.dlcs.toMutableList().apply { add(Dlc(0, "")) }
                onConfigChange(config.copy(r2 = config.r2.copy(dlcs = updated)))
            },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text("Add DLC")
        }
        DefaultSpacer()

        // R2: Items
        Text("Items (r2)")
        config.r2.items.forEachIndexed { index, item ->
            Column(Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                OutlinedTextField(
                    value = item.ProductID.toString(),
                    onValueChange = { newId ->
                        val updated = config.r2.items.toMutableList()
                        updated[index] = item.copy(ProductID = newId.toIntOrNull() ?: 0)
                        onConfigChange(config.copy(r2 = config.r2.copy(items = updated)))
                    },
                    label = { Text("ProductID") },
                    singleLine = true,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = item.name,
                    onValueChange = { newName ->
                        val updated = config.r2.items.toMutableList()
                        updated[index] = item.copy(name = newName)
                        onConfigChange(config.copy(r2 = config.r2.copy(items = updated)))
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                androidx.compose.material.Button(
                    onClick = {
                        val updated = config.r2.items.toMutableList().apply { removeAt(index) }
                        onConfigChange(config.copy(r2 = config.r2.copy(items = updated)))
                    },
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text("Remove")
                }
            }
        }
        androidx.compose.material.Button(
            onClick = {
                val updated = config.r2.items.toMutableList().apply { add(Item(0, "")) }
                onConfigChange(config.copy(r2 = config.r2.copy(items = updated)))
            },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text("Add Item")
        }
    }
}
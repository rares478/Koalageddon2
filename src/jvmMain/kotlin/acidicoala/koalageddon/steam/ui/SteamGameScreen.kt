package acidicoala.koalageddon.steam.ui

import acidicoala.koalageddon.core.ui.composable.*
import acidicoala.koalageddon.core.ui.composition.LocalStrings
import acidicoala.koalageddon.core.ui.theme.AppTheme
import acidicoala.koalageddon.core.ui.theme.DefaultContentPadding
import acidicoala.koalageddon.core.ui.theme.DefaultMaxWidth
import acidicoala.koalageddon.core.use_case.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.kodein.di.compose.localDI
import org.kodein.di.instance
import androidx.compose.material.ExperimentalMaterialApi

@Composable
fun SteamGameScreen() {
    val screenModel: SteamGameScreenModel by localDI().instance()
    val state by screenModel.collectAsState()
    val strings = LocalStrings.current

    LaunchedEffect(screenModel) {
        screenModel.onRefreshState()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = DefaultMaxWidth)
                .padding(DefaultContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(Modifier.weight(1f)) {
                        SectionLabel(
                            icon = Icons.Default.Games,
                            label = "Steam Game Unlocker"
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { screenModel.onSearchQueryChanged(it) },
                        label = { Text("Search games") },
                        singleLine = true,
                        modifier = Modifier.width(220.dp)
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Detected ${state.games.size} Steam games",
                            style = MaterialTheme.typography.subtitle1
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { screenModel.onRefreshGames() },
                            enabled = !state.isRefreshing
                        ) {
                            if (state.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colors.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Refreshing...")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refresh Games")
                            }
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Add Game", style = MaterialTheme.typography.subtitle1)
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = { screenModel.onOpenAddGameDialog() }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Game")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
            }
            if (state.games.isEmpty() && !state.isRefreshing) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Games,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Steam games detected",
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Make sure Steam is installed and you have games in your library",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            } else {
                items(screenModel.filteredGames) { game ->
                    GameCard(
                        game = game,
                        onInstall = { mode -> screenModel.onInstallGame(game, mode) },
                        onUninstall = { screenModel.onUninstallGame(game) },
                        isInstalling = state.installingGame == game.executablePath,
                        isUninstalling = state.uninstallingGame == game.executablePath
                    )
                }
            }
            if (state.operationMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = if (state.operationSuccess == true) 
                            MaterialTheme.colors.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colors.error.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (state.operationSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (state.operationSuccess == true) 
                                    MaterialTheme.colors.primary 
                                else MaterialTheme.colors.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.operationMessage!!,
                                color = if (state.operationSuccess == true) 
                                    MaterialTheme.colors.primary 
                                else MaterialTheme.colors.error
                            )
                        }
                    }
                }
            }
        }
        if (state.showAddGameDialog) {
            @OptIn(ExperimentalMaterialApi::class)
            AddGameDialog(
                name = state.addGameName,
                path = state.addGamePath,
                mode = state.addGameMode,
                error = state.addGameError,
                onNameChange = screenModel::onAddGameNameChange,
                onPathChange = screenModel::onAddGamePathChange,
                onModeChange = screenModel::onAddGameModeChange,
                onBrowse = {
                    // Use JFileChooser for file picking
                    val chooser = javax.swing.JFileChooser()
                    chooser.fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
                    chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Executable Files", "exe")
                    val result = chooser.showOpenDialog(null)
                    if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                        screenModel.onAddGamePathChange(chooser.selectedFile.absolutePath)
                    }
                },
                onConfirm = screenModel::onAddGame,
                onCancel = screenModel::onCloseAddGameDialog
            )
        }
    }
}

@Composable
private fun GameCard(
    game: SteamGame,
    onInstall: (InstallSteamGameUnlocker.InstallationMode) -> Unit,
    onUninstall: () -> Unit,
    isInstalling: Boolean,
    isUninstalling: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Game header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Games,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.gameName,
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${game.architecture.name} • ${game.executablePath}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }

                val statusColor = when (game.installationStatus) {
                    is GameInstallationStatus.NotInstalled -> MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                    is GameInstallationStatus.HookMode -> MaterialTheme.colors.primary
                    is GameInstallationStatus.ProxyMode -> MaterialTheme.colors.secondary
                }
                
                val statusText = when (game.installationStatus) {
                    is GameInstallationStatus.NotInstalled -> "Not Installed"
                    is GameInstallationStatus.HookMode -> "Hook Mode"
                    is GameInstallationStatus.ProxyMode -> "Proxy Mode"
                }
                
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.caption,
                        color = statusColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            when (game.installationStatus) {
                is GameInstallationStatus.NotInstalled -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onInstall(InstallSteamGameUnlocker.InstallationMode.Hook) },
                            modifier = Modifier.weight(1f),
                            enabled = !isInstalling
                        ) {
                            if (isInstalling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colors.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Installing...")
                            } else {
                                Icon(Icons.Default.Download, contentDescription = "Install Hook")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Install Hook")
                            }
                        }
                        
                        Button(
                            onClick = { onInstall(InstallSteamGameUnlocker.InstallationMode.Proxy) },
                            modifier = Modifier.weight(1f),
                            enabled = !isInstalling
                        ) {
                            if (isInstalling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colors.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Installing...")
                            } else {
                                Icon(Icons.Default.Download, contentDescription = "Install Proxy")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Install Proxy")
                            }
                        }
                    }
                }
                
                is GameInstallationStatus.HookMode, is GameInstallationStatus.ProxyMode -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onInstall(InstallSteamGameUnlocker.InstallationMode.Hook) },
                            modifier = Modifier.weight(1f),
                            enabled = !isInstalling && game.installationStatus !is GameInstallationStatus.HookMode
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Switch to Hook")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Switch to Hook")
                        }
                        
                        OutlinedButton(
                            onClick = { onInstall(InstallSteamGameUnlocker.InstallationMode.Proxy) },
                            modifier = Modifier.weight(1f),
                            enabled = !isInstalling && game.installationStatus !is GameInstallationStatus.ProxyMode
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Switch to Proxy")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Switch to Proxy")
                        }
                        
                        Button(
                            onClick = onUninstall,
                            enabled = !isUninstalling,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.error
                            )
                        ) {
                            if (isUninstalling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colors.onError,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Uninstalling...")
                            } else {
                                Icon(Icons.Default.Delete, contentDescription = "Uninstall")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Uninstall")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AddGameDialog(
    name: String,
    path: String,
    mode: InstallSteamGameUnlocker.InstallationMode,
    error: String?,
    onNameChange: (String) -> Unit,
    onPathChange: (String) -> Unit,
    onModeChange: (InstallSteamGameUnlocker.InstallationMode) -> Unit,
    onBrowse: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add Game") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Game Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = path,
                        onValueChange = onPathChange,
                        label = { Text("Executable Path") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onBrowse) {
                        Text("Browse")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode:")
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(
                        selected = mode is InstallSteamGameUnlocker.InstallationMode.Hook,
                        onClick = { onModeChange(InstallSteamGameUnlocker.InstallationMode.Hook) }
                    )
                    Text("Hook")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = mode is InstallSteamGameUnlocker.InstallationMode.Proxy,
                        onClick = { onModeChange(InstallSteamGameUnlocker.InstallationMode.Proxy) }
                    )
                    Text("Proxy")
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colors.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Add") }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}
package acidicoala.koalageddon.steam.ui

import acidicoala.koalageddon.core.model.*
import acidicoala.koalageddon.core.use_case.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import acidicoala.koalageddon.core.logging.AppLogger
import acidicoala.koalageddon.core.use_case.DetectGameArchitecture

class SteamGameScreenModel(
    override val di: DI,
    private val stateFlow: MutableStateFlow<State> = MutableStateFlow(State())
) : DIAware, StateFlow<SteamGameScreenModel.State> by stateFlow {
    
    data class State(
        val games: List<SteamGame> = emptyList(),
        val isRefreshing: Boolean = false,
        val installingGame: String? = null,
        val uninstallingGame: String? = null,
        val operationMessage: String? = null,
        val operationSuccess: Boolean? = null,
        val searchQuery: String = "",
        val showAddGameDialog: Boolean = false,
        val addGameName: String = "",
        val addGamePath: String = "",
        val addGameMode: InstallSteamGameUnlocker.InstallationMode = InstallSteamGameUnlocker.InstallationMode.Hook,
        val addGameError: String? = null
    )

    private val detectSteamGames: DetectSteamGames by instance()
    private val installSteamGameUnlocker: InstallSteamGameUnlocker by instance()
    private val uninstallSteamGameUnlocker: UninstallSteamGameUnlocker by instance()
    private val hookedGamesCache: HookedGamesCache by instance()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val logger: AppLogger by instance()
    private val detectGameArchitecture: DetectGameArchitecture by instance()

    fun onRefreshState() {
        onRefreshGames()
    }

    fun onRefreshGames() {
        val cached = hookedGamesCache.read().map {
            SteamGame(
                executablePath = it.path,
                gameName = it.gameName,
                architecture = ISA.valueOf(it.architecture),
                installationStatus = when (it.method) {
                    "Proxy" -> GameInstallationStatus.ProxyMode
                    "Hook" -> GameInstallationStatus.HookMode
                    else -> GameInstallationStatus.NotInstalled
                }
            )
        }
        stateFlow.update { it.copy(games = cached, isRefreshing = true) }

        scope.launch {
            try {
                val games = detectSteamGames()
                stateFlow.update { 
                    it.copy(
                        games = games,
                        isRefreshing = false
                    ) 
                }
                val hooked = games.filter { it.installationStatus is GameInstallationStatus.HookMode || it.installationStatus is GameInstallationStatus.ProxyMode }
                val cacheEntries = hooked.map {
                    HookedGameEntry(
                        gameName = it.gameName,
                        architecture = it.architecture.name,
                        path = it.executablePath,
                        method = when (it.installationStatus) {
                            is GameInstallationStatus.ProxyMode -> "Proxy"
                            is GameInstallationStatus.HookMode -> "Hook"
                            else -> ""
                        }
                    )
                }
                hookedGamesCache.write(cacheEntries)
            } catch (e: Exception) {
                logger.error(e, "Failed to detect games")
                stateFlow.update { 
                    it.copy(
                        games = emptyList(),
                        isRefreshing = false,
                        operationMessage = "Failed to detect games: ${e.message}",
                        operationSuccess = false
                    ) 
                }
            }
        }
    }

    fun onInstallGame(game: SteamGame, mode: InstallSteamGameUnlocker.InstallationMode) {
        scope.launch {
            stateFlow.update { 
                it.copy(
                    installingGame = game.executablePath,
                    operationMessage = null,
                    operationSuccess = null
                ) 
            }

            try {
                val result = installSteamGameUnlocker(game.executablePath, mode)

                stateFlow.update {
                    it.copy(
                        installingGame = null,
                        operationMessage = when (result) {
                            is InstallSteamGameUnlocker.InstallationResult.Success -> 
                                "Successfully installed ${mode::class.simpleName} mode for ${game.gameName}"
                            is InstallSteamGameUnlocker.InstallationResult.Error -> result.message
                        },
                        operationSuccess = result is InstallSteamGameUnlocker.InstallationResult.Success
                    )
                }

                if (result is InstallSteamGameUnlocker.InstallationResult.Success) {
                    val method = when (mode) {
                        is InstallSteamGameUnlocker.InstallationMode.Proxy -> "Proxy"
                        is InstallSteamGameUnlocker.InstallationMode.Hook -> "Hook"
                    }
                    hookedGamesCache.addOrUpdate(
                        HookedGameEntry(
                            gameName = game.gameName,
                            architecture = game.architecture.name,
                            path = game.executablePath,
                            method = method
                        )
                    )
                    onRefreshGames()
                } else if (result is InstallSteamGameUnlocker.InstallationResult.Error) {
                    logger.error(Exception(result.message), "Installation failed for ${game.gameName}")
                }
            } catch (e: Exception) {
                logger.error(e, "Installation failed for ${game.gameName}")
                stateFlow.update {
                    it.copy(
                        installingGame = null,
                        operationMessage = "Installation failed: ${e.message}",
                        operationSuccess = false
                    )
                }
            }
        }
    }

    fun onUninstallGame(game: SteamGame) {
        scope.launch {
            stateFlow.update { 
                it.copy(
                    uninstallingGame = game.executablePath,
                    operationMessage = null,
                    operationSuccess = null
                ) 
            }

            try {
                val result = uninstallSteamGameUnlocker(game.executablePath)

                stateFlow.update {
                    it.copy(
                        uninstallingGame = null,
                        operationMessage = when (result) {
                            is UninstallSteamGameUnlocker.UninstallResult.Success -> 
                                "Successfully uninstalled unlocker from ${game.gameName}"
                            is UninstallSteamGameUnlocker.UninstallResult.Error -> result.message
                        },
                        operationSuccess = result is UninstallSteamGameUnlocker.UninstallResult.Success
                    )
                }

                if (result is UninstallSteamGameUnlocker.UninstallResult.Success) {
                    hookedGamesCache.removeByPath(game.executablePath)
                    onRefreshGames()
                } else if (result is UninstallSteamGameUnlocker.UninstallResult.Error) {
                    logger.error(Exception(result.message), "Uninstallation failed for ${game.gameName}")
                }
            } catch (e: Exception) {
                logger.error(e, "Uninstallation failed for ${game.gameName}")
                stateFlow.update {
                    it.copy(
                        uninstallingGame = null,
                        operationMessage = "Uninstallation failed: ${e.message}",
                        operationSuccess = false
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        stateFlow.update { it.copy(searchQuery = query) }
    }

    val filteredGames: List<SteamGame>
        get() = stateFlow.value.let { state ->
            if (state.searchQuery.isBlank()) state.games
            else state.games.filter { it.gameName.contains(state.searchQuery, ignoreCase = true) }
        }

    fun onOpenAddGameDialog() {
        stateFlow.update { it.copy(showAddGameDialog = true, addGameName = "", addGamePath = "", addGameMode = InstallSteamGameUnlocker.InstallationMode.Hook, addGameError = null) }
    }

    fun onCloseAddGameDialog() {
        stateFlow.update { it.copy(showAddGameDialog = false, addGameError = null) }
    }

    fun onAddGameNameChange(name: String) {
        stateFlow.update { it.copy(addGameName = name) }
    }

    fun onAddGamePathChange(path: String) {
        stateFlow.update { it.copy(addGamePath = path) }
    }

    fun onAddGameModeChange(mode: InstallSteamGameUnlocker.InstallationMode) {
        stateFlow.update { it.copy(addGameMode = mode) }
    }

    fun onAddGame() {
        val state = stateFlow.value
        if (state.addGameName.isBlank() || state.addGamePath.isBlank()) {
            stateFlow.update { it.copy(addGameError = "Name and path are required") }
            return
        }
        val file = java.io.File(state.addGamePath)
        if (!file.exists() || !file.isFile) {
            stateFlow.update { it.copy(addGameError = "Executable path is invalid") }
            return
        }
        val arch = try { detectGameArchitecture(file) } catch (e: Exception) { ISA.X86 }
        val newGame = SteamGame(
            executablePath = state.addGamePath,
            gameName = state.addGameName,
            architecture = arch,
            installationStatus = when (state.addGameMode) {
                InstallSteamGameUnlocker.InstallationMode.Hook -> GameInstallationStatus.HookMode
                InstallSteamGameUnlocker.InstallationMode.Proxy -> GameInstallationStatus.ProxyMode
            }
        )
        
        if (state.addGameMode == InstallSteamGameUnlocker.InstallationMode.Hook || state.addGameMode == InstallSteamGameUnlocker.InstallationMode.Proxy) {
            hookedGamesCache.addOrUpdate(
                HookedGameEntry(
                    gameName = newGame.gameName,
                    architecture = newGame.architecture.name,
                    path = newGame.executablePath,
                    method = when (state.addGameMode) {
                        InstallSteamGameUnlocker.InstallationMode.Hook -> "Hook"
                        InstallSteamGameUnlocker.InstallationMode.Proxy -> "Proxy"
                    }
                )
            )
        }
        stateFlow.update {
            it.copy(
                games = it.games + newGame,
                showAddGameDialog = false,
                addGameName = "",
                addGamePath = "",
                addGameError = null
            )
        }
    }
} 
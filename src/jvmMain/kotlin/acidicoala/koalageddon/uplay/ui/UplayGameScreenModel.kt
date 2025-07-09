package acidicoala.koalageddon.uplay.ui

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
import acidicoala.koalageddon.core.use_case.DetectInstallationStatus
import acidicoala.koalageddon.uplay.domain.use_case.DetectUplayGames
import acidicoala.koalageddon.uplay.domain.use_case.UninstallUplayGameUnlocker
import acidicoala.koalageddon.uplay.model.UplayGame
import java.io.File

class UplayGameScreenModel(
    override val di: DI,
    private val stateFlow: MutableStateFlow<State> = MutableStateFlow(State())
) : DIAware, StateFlow<UplayGameScreenModel.State> by stateFlow {
    
    data class State(
        val games: List<UplayGame> = emptyList(),
        val isRefreshing: Boolean = false,
        val installingGame: String? = null,
        val uninstallingGame: String? = null,
        val operationMessage: String? = null,
        val operationSuccess: Boolean? = null,
        val searchQuery: String = "",
        val showAddGameDialog: Boolean = false,
        val addGameName: String = "",
        val addGamePath: String = "",
        val addGameMode: InstallUplayGameUnlocker.InstallationMode = InstallUplayGameUnlocker.InstallationMode.Hook,
        val addGameError: String? = null
    )

    private val detectUplayGames: DetectUplayGames by instance()
    private val installUplayGameUnlocker: InstallUplayGameUnlocker by instance()
    private val uninstallUplayGameUnlocker: UninstallUplayGameUnlocker by instance()
    private val hookedGamesCache: HookedGamesCache by instance()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val logger: AppLogger by instance()
    private val detectGameArchitecture: DetectGameArchitecture by instance()
    private val detectInstallationStatus: DetectInstallationStatus by instance()

    fun onRefreshState() {
        onRefreshGames()
    }

    fun onRefreshGames() {
        val cached = hookedGamesCache.read().map {
            UplayGame(
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
                val detectedGames = detectUplayGames.scan()
                val games = detectUplayGames.toUplayGames(
                    detected = detectedGames,
                    detectGameArchitecture = { file -> detectGameArchitecture(file) },
                    detectGameInstallStatus = { exePath -> detectInstallationStatus(File(exePath).parentFile) }
                )
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

    fun onInstallGame(game: UplayGame, mode: InstallUplayGameUnlocker.InstallationMode) {
        scope.launch {
            stateFlow.update { 
                it.copy(
                    installingGame = game.executablePath,
                    operationMessage = null,
                    operationSuccess = null
                ) 
            }

            try {
                val result = installUplayGameUnlocker(game.executablePath, mode)

                stateFlow.update {
                    it.copy(
                        installingGame = null,
                        operationMessage = when (result) {
                            is InstallUplayGameUnlocker.InstallationResult.Success -> 
                                "Successfully installed ${mode::class.simpleName} mode for ${game.gameName}"
                            is InstallUplayGameUnlocker.InstallationResult.Error -> result.message
                        },
                        operationSuccess = result is InstallUplayGameUnlocker.InstallationResult.Success
                    )
                }

                if (result is InstallUplayGameUnlocker.InstallationResult.Success) {
                    val method = when (mode) {
                        is InstallUplayGameUnlocker.InstallationMode.Proxy -> "Proxy"
                        is InstallUplayGameUnlocker.InstallationMode.Hook -> "Hook"
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
                } else if (result is InstallUplayGameUnlocker.InstallationResult.Error) {
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

    fun onUninstallGame(game: UplayGame) {
        scope.launch {
            stateFlow.update { 
                it.copy(
                    uninstallingGame = game.executablePath,
                    operationMessage = null,
                    operationSuccess = null
                ) 
            }

            try {
                val result = uninstallUplayGameUnlocker(game.executablePath)

                stateFlow.update {
                    it.copy(
                        uninstallingGame = null,
                        operationMessage = when (result) {
                            is UninstallUplayGameUnlocker.UninstallResult.Success -> 
                                "Successfully uninstalled unlocker from ${game.gameName}"
                            is UninstallUplayGameUnlocker.UninstallResult.Error -> result.message
                        },
                        operationSuccess = result is UninstallUplayGameUnlocker.UninstallResult.Success
                    )
                }

                if (result is UninstallUplayGameUnlocker.UninstallResult.Success) {
                    hookedGamesCache.removeByPath(game.executablePath)
                    onRefreshGames()
                } else if (result is UninstallUplayGameUnlocker.UninstallResult.Error) {
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

    val filteredGames: List<UplayGame>
        get() = stateFlow.value.let { state ->
            if (state.searchQuery.isBlank()) state.games
            else state.games.filter { it.gameName.contains(state.searchQuery, ignoreCase = true) }
        }

    fun onOpenAddGameDialog() {
        stateFlow.update { it.copy(showAddGameDialog = true, addGameName = "", addGamePath = "", addGameMode = InstallUplayGameUnlocker.InstallationMode.Hook, addGameError = null) }
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

    fun onAddGameModeChange(mode: InstallUplayGameUnlocker.InstallationMode) {
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
        val newGame = UplayGame(
            executablePath = state.addGamePath,
            gameName = state.addGameName,
            architecture = arch,
            installationStatus = when (state.addGameMode) {
                InstallUplayGameUnlocker.InstallationMode.Hook -> GameInstallationStatus.HookMode
                InstallUplayGameUnlocker.InstallationMode.Proxy -> GameInstallationStatus.ProxyMode
            }
        )
        
        if (state.addGameMode == InstallUplayGameUnlocker.InstallationMode.Hook || state.addGameMode == InstallUplayGameUnlocker.InstallationMode.Proxy) {
            hookedGamesCache.addOrUpdate(
                HookedGameEntry(
                    gameName = newGame.gameName,
                    architecture = newGame.architecture.name,
                    path = newGame.executablePath,
                    method = when (state.addGameMode) {
                        InstallUplayGameUnlocker.InstallationMode.Hook -> "Hook"
                        InstallUplayGameUnlocker.InstallationMode.Proxy -> "Proxy"
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
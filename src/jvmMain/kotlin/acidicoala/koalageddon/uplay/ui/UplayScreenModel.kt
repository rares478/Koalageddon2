package acidicoala.koalageddon.uplay.ui

import acidicoala.koalageddon.core.logging.AppLogger
import acidicoala.koalageddon.core.model.*
import acidicoala.koalageddon.core.use_case.*
import acidicoala.koalageddon.core.io.appJson
import acidicoala.koalageddon.uplay.model.*
import acidicoala.koalageddon.core.values.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.io.File
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.milliseconds
import acidicoala.koalageddon.core.use_case.InstallUplaySleepApiUnlocker
import acidicoala.koalageddon.core.model.KoalaTool.SleepAPI
import acidicoala.koalageddon.core.model.KoalaTool.SleepAPI.Config
import acidicoala.koalageddon.core.use_case.InstallUplayGameUnlocker
import acidicoala.koalageddon.steam.domain.use_case.ReloadUplayConfig
import acidicoala.koalageddon.uplay.domain.use_case.DetectUplayGames
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UplayScreenModel(
    override val di: DI,
    private val stateFlow: MutableStateFlow<State> = MutableStateFlow(State())
) : DIAware, StateFlow<UplayScreenModel.State> by stateFlow {
    data class State(
        val installProgressMessage: ILangString? = null,
        val installationChecklist: InstallationChecklist? = null,
        val config: Config? = null,
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
        val addGameError: String? = null,
        val isUplayRunning: Boolean = false,
        val logFileExists: Boolean = false,
        val sleepApiInstalled: Boolean = false,
        val sleepApiProgress: String? = null,
        val isInitialized: Boolean = false,
    )

    private val paths: AppPaths by instance()
    private val logger: AppLogger by instance()
    private val getInstallationChecklist: GetInstallationChecklist by instance()
    private val showSnackbar: ShowSnackbar by instance()
    private val updateUnlockerConfig: UpdateUnlockerConfig by instance()
    private val modifyInstallationStatus: ModifyInstallationStatus by instance()
    private val isProcessRunning: IsProcessRunning by instance()
    private val openResourceLink: OpenResourceLink by instance()
    private val installUplaySleepApiUnlocker: InstallUplaySleepApiUnlocker by instance()
    private val installUplayGameUnlocker: InstallUplayGameUnlocker by instance()
    private val detectGameArchitecture: DetectGameArchitecture by instance()
    private val uplayGamesCache: UplayGamesCache by instance()
    private val detectUplayGames: DetectUplayGames by instance()
    private val mutex = Mutex()
    private val reloadUplayConfig: ReloadUplayConfig by instance()

    private val scope = CoroutineScope(Dispatchers.IO)

    val filteredGames: List<UplayGame>
        get() = value.games.filter {
            it.gameName.contains(value.searchQuery, ignoreCase = true) ||
            it.executablePath.contains(value.searchQuery, ignoreCase = true)
        }

    fun onRefreshState() {
        scope.launch {
            stateFlow.update {
                it.copy(
                    installationChecklist = null,
                    config = null,
                )
            }

            stateFlow.update {
                it.copy(
                    logFileExists = paths.getUnlockerLog(SleepAPI).exists(),
                    installationChecklist = getInstallationChecklist(store = Store.Ubisoft),
                    config = try {
                        SleepAPI.parseConfig(paths.getUnlockerConfig(SleepAPI))
                    } catch (e: Exception) {
                        null
                    },
                )
            }
        }

        scope.launch {
            while (true) {
                stateFlow.update {
                    it.copy(
                        isUplayRunning = isProcessRunning(paths.getStoreExecutablePath(Store.Ubisoft))
                    )
                }

                delay(500.milliseconds)
            }
        }
    }

    fun onReloadConfig() {
        scope.launch {
            mutex.withLock {
                reloadUplayConfig()
            }
        }
    }

    fun onModifyInstallation() {
        scope.launch {
            value.installationChecklist?.installationStatus?.let { currentStatus ->
                if (currentStatus is InstallationStatus.Updating) {
                    return@let
                }

                try {
                    when (currentStatus) {
                        is InstallationStatus.Installed -> {
                            installUplaySleepApiUnlocker.uninstall(Store.Ubisoft)
                            showSnackbar(message = LangString { removalSuccess })
                        }
                        is InstallationStatus.NotInstalled -> {
                            installUplaySleepApiUnlocker.install(Store.Ubisoft).collect { langString ->
                                stateFlow.update {
                                    it.copy(installProgressMessage = langString)
                                }
                            }
                            showSnackbar(message = LangString { installationSuccess })
                        }
                        is InstallationStatus.Updating -> throw IllegalStateException()
                    }
                } catch (e: Exception) {
                    logger.error(e, "Error modifying installation status")
                    showSnackbar(message = LangString {
                        when (currentStatus) {
                            is InstallationStatus.Installed -> removalError
                            is InstallationStatus.NotInstalled -> installationError
                            is InstallationStatus.Updating -> throw IllegalStateException()
                        }
                    })
                } finally {
                    stateFlow.update {
                        it.copy(installProgressMessage = null)
                    }

                    onRefreshState()
                }
            }
        }
    }

    fun onConfigChange(config: Config) {
        stateFlow.update {
            it.copy(config = config)
        }

        updateUnlockerConfig(config, SleepAPI)
    }

    fun onUnlockerClick() {
        scope.launch {
            openResourceLink(java.net.URI("https://github.com/rares478/SleepAPI"))
        }
    }

    fun onOpenLogs() {
        scope.launch {
            val logFile = paths.getUnlockerLog(SleepAPI)
            if (logFile.exists()) {
                openResourceLink(logFile)
            } else {
                showSnackbar(LangString { "Log file does not exist." })
            }
        }
    }
} 
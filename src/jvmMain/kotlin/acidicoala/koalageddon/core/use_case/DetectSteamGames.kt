package acidicoala.koalageddon.core.use_case

import acidicoala.koalageddon.core.model.ISA
import acidicoala.koalageddon.core.model.Store
import acidicoala.koalageddon.core.logging.AppLogger
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

data class SteamGame(
    val executablePath: String,
    val gameName: String,
    val architecture: ISA,
    val installationStatus: GameInstallationStatus
)

sealed class GameInstallationStatus {
    object NotInstalled : GameInstallationStatus()
    object HookMode : GameInstallationStatus()
    object ProxyMode : GameInstallationStatus()
}

class DetectSteamGames(override val di: DI) : DIAware {
    private val logger: AppLogger by instance()
    private val smokeApiDetector: SmokeApiDetector by instance()
    private val detectGameArchitecture: DetectGameArchitecture by instance()
    
    // List of known utility exe names to ignore for now
    private val ignoreExeNames = setOf(
        "language-changer.exe", "dlc-uninstaller.exe", "unins000.exe", "support.exe", "launcher.exe", "config.exe", "settings.exe", "setup.exe", "install.exe", "uninstaller.exe", "unitycrashhandler64.exe", "ueprereqsetup_x64.exe", "crashreportclient.exe", "vconsole2.exe"
    )
    
    operator fun invoke(): List<SteamGame> {
        val steamDirectory = Store.Steam.directory
        if (!steamDirectory.exists()) {
            logger.debug("Steam directory not found")
            return emptyList()
        }
        
        val games = mutableListOf<SteamGame>()

        val steamAppsDir = steamDirectory.resolve("steamapps")
        if (steamAppsDir.exists()) {
            val libraryFolders = listOf(steamAppsDir) + 
                findAdditionalLibraryFolders(steamAppsDir.resolve("libraryfolders.vdf"))
            
            for (libraryFolder in libraryFolders) {
                if (libraryFolder.exists()) {
                    val commonDir = libraryFolder.resolve("common")
                    if (commonDir.exists()) {
                        games.addAll(findGamesInDirectory(commonDir))
                    }
                }
            }
        }
        
        // Remove duplicates
        return games.distinctBy { game ->
            runCatching { File(game.executablePath).canonicalPath.lowercase() }.getOrElse { game.executablePath.lowercase() }
        }
    }
    
    private fun findAdditionalLibraryFolders(libraryFoldersFile: Path): List<Path> {
        if (!libraryFoldersFile.exists()) return emptyList()
        
        return try {
            val content = libraryFoldersFile.toFile().readText()
            val paths = mutableListOf<Path>()
            
            // Simple parsing of libraryfolders.vdf to find additional paths
            val pathRegex = "\"path\"\\s+\"([^\"]+)\"".toRegex()
            pathRegex.findAll(content).forEach { matchResult ->
                val path = matchResult.groupValues[1]
                paths.add(Path.of(path).resolve("steamapps"))
            }
            
            paths
        } catch (e: Exception) {
            logger.error(e, "Error parsing libraryfolders.vdf")
            emptyList()
        }
    }
    
    private fun findGamesInDirectory(directory: Path): List<SteamGame> {
        val games = mutableListOf<SteamGame>()
        val seenExecutables = mutableSetOf<String>()
        
        if (!directory.exists()) return games
        
        directory.toFile().listFiles()?.forEach { gameDir ->
            if (gameDir.isDirectory) {
                val allExeFiles = gameDir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("exe", ignoreCase = true) }
                    .filterNot { ignoreExeNames.contains(it.name.lowercase()) }
                    .toList()
                
                // Prefer exe whose name matches the directory name
                val dirNameNorm = gameDir.name.lowercase().replace(" ", "").replace("_", "")
                val bestExe = allExeFiles.find {
                    val exeNameNorm = it.nameWithoutExtension.lowercase().replace(" ", "").replace("_", "")
                    exeNameNorm == dirNameNorm
                } ?: allExeFiles.maxByOrNull { it.length() } // fallback: largest exe
                
                if (bestExe != null) {
                    val canonicalPath = try { bestExe.canonicalPath.lowercase() } catch (e: Exception) { bestExe.absolutePath.lowercase() }
                    if (seenExecutables.add(canonicalPath)) {
                        try {
                            val architecture = detectGameArchitecture(bestExe)
                            val installationStatus = detectInstallationStatus(bestExe.parentFile, bestExe.name)
                            
                            games.add(SteamGame(
                                executablePath = bestExe.absolutePath,
                                gameName = gameDir.name,
                                architecture = architecture,
                                installationStatus = installationStatus
                            ))
                        } catch (e: Exception) {
                            logger.error(e, "Error processing game: ${gameDir.name}")
                        }
                    }
                }
            }
        }
        
        return games
    }
    
    private fun detectInstallationStatus(gameDirectory: File, exeName: String): GameInstallationStatus {
        // Check for Hook Mode
        val versionDll = File(gameDirectory, "version.dll")
        val smokeApiDll = File(gameDirectory, "SmokeAPI.dll")
        val smokeApiConfig = File(gameDirectory, "SmokeAPI.config.json")
        
        if (versionDll.exists() && smokeApiDll.exists() && smokeApiConfig.exists() && smokeApiDetector.isSmokeApiDll(smokeApiDll)) {
            return GameInstallationStatus.HookMode
        }
        
        // Check for Proxy Mode
        val steamApiDll = File(gameDirectory, "steam_api.dll")
        val steamApi64Dll = File(gameDirectory, "steam_api64.dll")
        val steamApiOriginal = File(gameDirectory, "steam_api_o.dll")
        val steamApi64Original = File(gameDirectory, "steam_api64_o.dll")
        
        if ((steamApiDll.exists() && steamApiOriginal.exists() && smokeApiDetector.isSmokeApiDll(steamApiDll)) || 
            (steamApi64Dll.exists() && steamApi64Original.exists() && smokeApiDetector.isSmokeApiDll(steamApi64Dll))) {
            return GameInstallationStatus.ProxyMode
        }
        
        return GameInstallationStatus.NotInstalled
    }
} 
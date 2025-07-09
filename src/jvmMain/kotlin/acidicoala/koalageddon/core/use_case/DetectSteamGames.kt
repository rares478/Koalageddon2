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

class DetectSteamGames(override val di: DI) : DIAware {
    private val logger: AppLogger by instance()
    private val detectGameArchitecture: DetectGameArchitecture by instance()
    private val detectInstallationStatus: DetectInstallationStatus by instance()
    
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
        
        return games.distinctBy { game ->
            runCatching { File(game.executablePath).canonicalPath.lowercase() }.getOrElse { game.executablePath.lowercase() }
        }
    }
    
    private fun findAdditionalLibraryFolders(libraryFoldersFile: Path): List<Path> {
        if (!libraryFoldersFile.exists()) return emptyList()
        
        return try {
            val content = libraryFoldersFile.toFile().readText()
            val paths = mutableListOf<Path>()
            
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
                            val installationStatus = detectInstallationStatus(bestExe.parentFile)
                            
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
    
} 
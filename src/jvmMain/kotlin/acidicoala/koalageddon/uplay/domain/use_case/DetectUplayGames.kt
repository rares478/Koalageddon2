package acidicoala.koalageddon.uplay.domain.use_case

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.kodein.di.DI
import org.kodein.di.DIAware
import acidicoala.koalageddon.core.model.ISA
import acidicoala.koalageddon.core.use_case.GameInstallationStatus
import acidicoala.koalageddon.uplay.model.UplayGame
import java.io.File

data class DetectedUplayGame(
    val gameId: String,
    val installDir: String,
    val startExe: String?,
    val displayName: String?
)

class DetectUplayGames(override val di: DI) : DIAware {
    private val baseKey = "SOFTWARE\\WOW6432Node\\Ubisoft\\Launcher\\Installs"

    fun scan(): List<DetectedUplayGame> {
        val results = mutableListOf<DetectedUplayGame>()
        if (!Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, baseKey)) return results
        val gameIds = Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE, baseKey)
        for (gameId in gameIds) {
            val gameKey = "$baseKey\\$gameId"
            try {
                val props = Advapi32Util.registryGetValues(WinReg.HKEY_LOCAL_MACHINE, gameKey)
                val installDir = props["InstallDir"] as? String ?: continue
                val startExe = props["StartExe"] as? String
                val displayName = props["DisplayName"] as? String
                results.add(
                    DetectedUplayGame(
                        gameId = gameId,
                        installDir = installDir,
                        startExe = startExe,
                        displayName = displayName
                    )
                )
            } catch (_: Exception) {
            }
        }
        return results
    }

    fun toUplayGames(
        detected: List<DetectedUplayGame>,
        detectGameArchitecture: (File) -> ISA,
        detectGameInstallStatus: (String) -> GameInstallationStatus
    ): List<UplayGame> {
        return detected.mapNotNull { game ->
            val exePath = if (game.startExe != null) {
                File(game.installDir, game.startExe).absolutePath
            } else {
                File(game.installDir).listFiles()?.firstOrNull { it.extension.equals("exe", true) }?.absolutePath
            }
            if (exePath != null && File(exePath).exists()) {
                val arch = detectGameArchitecture(File(exePath))
                UplayGame(
                    executablePath = exePath,
                    gameName = game.displayName ?: File(game.installDir).name,
                    architecture = arch,
                    installationStatus = detectGameInstallStatus(exePath)
                )
            } else null
        }
    }
} 
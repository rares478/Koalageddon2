package acidicoala.koalageddon.core.use_case

import java.io.File

sealed class GameInstallationStatus {
    object NotInstalled : GameInstallationStatus()
    object HookMode : GameInstallationStatus()
    object ProxyMode : GameInstallationStatus()
}

class DetectInstallationStatus {
    operator fun invoke(gameDirectory: File): GameInstallationStatus {
        val versionDll = File(gameDirectory, "version.dll")
        if (versionDll.exists()) {
            val smokeApiDll = File(gameDirectory, "SmokeAPI.dll")
            val sleepApiDll = File(gameDirectory, "SleepAPI64.dll")
            return when {
                smokeApiDll.exists() || sleepApiDll.exists() -> GameInstallationStatus.ProxyMode
                else -> GameInstallationStatus.NotInstalled
            }
        } else {
            // Steam Hook Mode
            val steamApiDll = File(gameDirectory, "steam_api.dll")
            val steamApi64Dll = File(gameDirectory, "steam_api64.dll")
            val steamApiOriginal = File(gameDirectory, "steam_api_o.dll")
            val steamApi64Original = File(gameDirectory, "steam_api64_o.dll")
            if ((steamApiDll.exists() && steamApiOriginal.exists()) ||
                (steamApi64Dll.exists() && steamApi64Original.exists())) {
                return GameInstallationStatus.HookMode
            }
            // Uplay Hook Mode
            val uplayR1Loader = File(gameDirectory, "uplay_r1_loader.dll")
            val uplayR1LoaderOriginal = File(gameDirectory, "uplay_r1_loader_o.dll")
            val upcR2Loader = File(gameDirectory, "upc_r2_loader64.dll")
            val upcR2LoaderOriginal = File(gameDirectory, "upc_r2_loader64_o.dll")
            if ((uplayR1Loader.exists() && uplayR1LoaderOriginal.exists()) ||
                (upcR2Loader.exists() && upcR2LoaderOriginal.exists())) {
                return GameInstallationStatus.HookMode
            }
        }
        return GameInstallationStatus.NotInstalled
    }
}

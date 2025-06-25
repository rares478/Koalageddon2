package acidicoala.koalageddon.core.use_case

import acidicoala.koalageddon.core.model.ISA
import acidicoala.koalageddon.core.logging.AppLogger
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class UninstallSteamGameUnlocker(override val di: DI) : DIAware {
    
    sealed class UninstallResult {
        object Success : UninstallResult()
        data class Error(val message: String) : UninstallResult()
    }
    
    private val logger: AppLogger by instance()
    private val detectGameArchitecture: DetectGameArchitecture by instance()
    
    operator fun invoke(gameExecutablePath: String): UninstallResult {
        return try {
            val gameFile = File(gameExecutablePath)
            if (!gameFile.exists()) {
                logger.error("Game executable not found: $gameExecutablePath")
                return UninstallResult.Error("Game executable not found: $gameExecutablePath")
            }
            
            val gameDirectory = gameFile.parentFile
            val isa = detectGameArchitecture(gameFile)
            
            // Check installation status and uninstall accordingly
            when (detectInstallationStatus(gameDirectory, gameFile.name)) {
                is GameInstallationStatus.HookMode -> uninstallHookMode(gameDirectory)
                is GameInstallationStatus.ProxyMode -> uninstallProxyMode(gameDirectory, isa)
                is GameInstallationStatus.NotInstalled -> UninstallResult.Success
            }
            
        } catch (e: Exception) {
            logger.error(e, "Uninstallation failed")
            UninstallResult.Error("Uninstallation failed: ${e.message}")
        }
    }
    
    private fun detectInstallationStatus(gameDirectory: File, exeName: String): GameInstallationStatus {
        // Check for Hook Mode
        val versionDll = File(gameDirectory, "version.dll")
        val smokeApiDll = File(gameDirectory, "SmokeAPI.dll")
        val smokeApiConfig = File(gameDirectory, "SmokeAPI.config.json")
        
        if (versionDll.exists() && smokeApiDll.exists() && smokeApiConfig.exists()) {
            return GameInstallationStatus.HookMode
        }
        
        // Check for Proxy Mode
        val steamApiDll = File(gameDirectory, "steam_api.dll")
        val steamApi64Dll = File(gameDirectory, "steam_api64.dll")
        val steamApiOriginal = File(gameDirectory, "steam_api_o.dll")
        val steamApi64Original = File(gameDirectory, "steam_api64_o.dll")
        
        if ((steamApiDll.exists() && steamApiOriginal.exists()) || 
            (steamApi64Dll.exists() && steamApi64Original.exists())) {
            return GameInstallationStatus.ProxyMode
        }
        
        return GameInstallationStatus.NotInstalled
    }
    
    private fun uninstallHookMode(gameDirectory: File): UninstallResult {
        try {
            // Remove version.dll
            val versionDll = File(gameDirectory, "version.dll")
            if (versionDll.exists()) {
                Files.delete(versionDll.toPath())
                logger.debug("Removed version.dll from ${gameDirectory.absolutePath}")
            }
            
            // Remove SmokeAPI.dll
            val smokeApiDll = File(gameDirectory, "SmokeAPI.dll")
            if (smokeApiDll.exists()) {
                Files.delete(smokeApiDll.toPath())
                logger.debug("Removed SmokeAPI.dll from ${gameDirectory.absolutePath}")
            }
            
            // Remove SmokeAPI.config.json
            val smokeApiConfig = File(gameDirectory, "SmokeAPI.config.json")
            if (smokeApiConfig.exists()) {
                Files.delete(smokeApiConfig.toPath())
                logger.debug("Removed SmokeAPI.config.json from ${gameDirectory.absolutePath}")
            }
            
            return UninstallResult.Success
        } catch (e: Exception) {
            logger.error(e, "Error uninstalling hook mode")
            return UninstallResult.Error("Failed to uninstall hook mode: ${e.message}")
        }
    }
    
    private fun uninstallProxyMode(gameDirectory: File, isa: ISA): UninstallResult {
        try {
            val steamApiName = if (isa == ISA.X86_64) "steam_api64.dll" else "steam_api.dll"
            val steamApiOriginalName = if (isa == ISA.X86_64) "steam_api64_o.dll" else "steam_api_o.dll"
            
            val steamApiFile = File(gameDirectory, steamApiName)
            val steamApiOriginalFile = File(gameDirectory, steamApiOriginalName)
            
            // Restore original steam_api.dll
            if (steamApiOriginalFile.exists()) {
                Files.move(steamApiOriginalFile.toPath(), steamApiFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                logger.debug("Restored original $steamApiName from ${gameDirectory.absolutePath}")
            } else {
                if (steamApiFile.exists()) {
                    Files.delete(steamApiFile.toPath())
                    logger.debug("Removed $steamApiName from ${gameDirectory.absolutePath}")
                }
            }

            val smokeApiConfig = File(gameDirectory, "SmokeAPI.config.json")
            if (smokeApiConfig.exists()) {
                Files.delete(smokeApiConfig.toPath())
                logger.debug("Removed SmokeAPI.config.json from ${gameDirectory.absolutePath}")
            }
            
            return UninstallResult.Success
        } catch (e: Exception) {
            logger.error(e, "Error uninstalling proxy mode")
            return UninstallResult.Error("Failed to uninstall proxy mode: ${e.message}")
        }
    }
} 
package acidicoala.koalageddon.uplay.domain.use_case

import acidicoala.koalageddon.core.model.ISA
import acidicoala.koalageddon.core.logging.AppLogger
import acidicoala.koalageddon.core.use_case.DetectGameArchitecture
import acidicoala.koalageddon.core.use_case.GameInstallationStatus
import acidicoala.koalageddon.core.use_case.DetectInstallationStatus
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class UninstallUplayGameUnlocker(override val di: DI) : DIAware {
    
    sealed class UninstallResult {
        object Success : UninstallResult()
        data class Error(val message: String) : UninstallResult()
    }
    
    private val logger: AppLogger by instance()
    private val detectGameArchitecture: DetectGameArchitecture by instance()
    private val detectInstallationStatus: DetectInstallationStatus by instance()
    operator fun invoke(gameExecutablePath: String): UninstallResult {
        return try {
            val gameFile = File(gameExecutablePath)
            if (!gameFile.exists()) {
                logger.error("Game executable not found: $gameExecutablePath")
                return UninstallResult.Error("Game executable not found: $gameExecutablePath")
            }
            
            val gameDirectory = gameFile.parentFile
            val isa = detectGameArchitecture(gameFile)
            
            when (detectInstallationStatus(gameDirectory)) {
                is GameInstallationStatus.HookMode -> uninstallHookMode(gameDirectory)
                is GameInstallationStatus.ProxyMode -> uninstallProxyMode(gameDirectory, isa)
                is GameInstallationStatus.NotInstalled -> UninstallResult.Success
            }
            
        } catch (e: Exception) {
            logger.error(e, "Uninstallation failed")
            UninstallResult.Error("Uninstallation failed: ${e.message}")
        }
    }
    
    
    private fun uninstallHookMode(gameDirectory: File): UninstallResult {
        try {
            val uplayR1Loader = File(gameDirectory, "uplay_r1_loader.dll")
            val uplayR1LoaderOriginal = File(gameDirectory, "uplay_r1_loader_o.dll")
            if (uplayR1Loader.exists()) {
                Files.delete(uplayR1Loader.toPath())
                logger.debug("Removed uplay_r1_loader.dll from "+gameDirectory.absolutePath)
                if (uplayR1LoaderOriginal.exists()) {
                    Files.move(uplayR1LoaderOriginal.toPath(), File(gameDirectory, "uplay_r1_loader.dll").toPath(), StandardCopyOption.REPLACE_EXISTING)
                    logger.debug("Restored uplay_r1_loader.dll from backup in "+gameDirectory.absolutePath)
                }
                val sleepApiLog = File(gameDirectory, "SleepAPI.log")
                if (sleepApiLog.exists()) {
                    Files.delete(sleepApiLog.toPath())
                    logger.debug("Removed SleepAPI.log from "+gameDirectory.absolutePath)
                }
                val sleepApiConfig = File(gameDirectory, "SleepAPI.config.json")
                if (sleepApiConfig.exists()) {
                    Files.delete(sleepApiConfig.toPath())
                    logger.debug("Removed SleepAPI.config.json from "+gameDirectory.absolutePath)
                }
                return UninstallResult.Success
            }
            val upcR2Loader = File(gameDirectory, "upc_r2_loader64.dll")
            val upcR2LoaderOriginal = File(gameDirectory, "upc_r2_loader64_o.dll")
            if (upcR2Loader.exists()) {
                Files.delete(upcR2Loader.toPath())
                logger.debug("Removed upc_r2_loader64.dll from "+gameDirectory.absolutePath)
                if (upcR2LoaderOriginal.exists()) {
                    Files.move(upcR2LoaderOriginal.toPath(), File(gameDirectory, "upc_r2_loader64.dll").toPath(), StandardCopyOption.REPLACE_EXISTING)
                    logger.debug("Restored upc_r2_loader64.dll from backup in "+gameDirectory.absolutePath)
                }
                val sleepApiLog = File(gameDirectory, "SleepAPI.log")
                if (sleepApiLog.exists()) {
                    Files.delete(sleepApiLog.toPath())
                    logger.debug("Removed SleepAPI.log from "+gameDirectory.absolutePath)
                }
                val sleepApiConfig = File(gameDirectory, "SleepAPI.config.json")
                if (sleepApiConfig.exists()) {
                    Files.delete(sleepApiConfig.toPath())
                    logger.debug("Removed SleepAPI.config.json from "+gameDirectory.absolutePath)
                }
                return UninstallResult.Success
            }
            val versionDll = File(gameDirectory, "version.dll")
            if (versionDll.exists()) {
                Files.delete(versionDll.toPath())
                logger.debug("Removed version.dll from "+gameDirectory.absolutePath)
            }
            val sleepApiDll = File(gameDirectory, "SleepAPI64.dll")
            if (sleepApiDll.exists()) {
                Files.delete(sleepApiDll.toPath())
                logger.debug("Removed SleepAPI64.dll from "+gameDirectory.absolutePath)
            }
            val sleepApiConfig = File(gameDirectory, "SleepAPI.config.json")
            if (sleepApiConfig.exists()) {
                Files.delete(sleepApiConfig.toPath())
                logger.debug("Removed SleepAPI.config.json from "+gameDirectory.absolutePath)
            }
            return UninstallResult.Success
        } catch (e: Exception) {
            logger.error(e, "Error uninstalling hook mode")
            return UninstallResult.Error("Failed to uninstall hook mode: ${e.message}")
        }
    }
    
    private fun uninstallProxyMode(gameDirectory: File, isa: ISA): UninstallResult {
        try {
            val versionDll = File(gameDirectory, "version.dll")
            if (versionDll.exists()) {
                try {
                    Files.delete(versionDll.toPath())
                    logger.debug("Removed version.dll from "+gameDirectory.absolutePath)
                } catch (e: Exception) {
                    logger.warn("Failed to remove version.dll: ${e.message}. This might be due to insufficient permissions or the file being in use.")
                }
            }
            val sleepApiDll = File(gameDirectory, "SleepAPI64.dll")
            if (sleepApiDll.exists()) {
                try {
                    Files.delete(sleepApiDll.toPath())
                    logger.debug("Removed SleepAPI64.dll from "+gameDirectory.absolutePath)
                } catch (e: Exception) {
                    logger.warn("Failed to remove SleepAPI64.dll: ${e.message}")
                }
            }
            val koaloaderConfig = File(gameDirectory, "Koaloader.config.json")
            if (koaloaderConfig.exists()) {
                Files.delete(koaloaderConfig.toPath())
                logger.debug("Removed Koaloader.config.json from "+gameDirectory.absolutePath)
            }
            val smokeApiConfig = File(gameDirectory, "SmokeAPI.config.json")
            if (smokeApiConfig.exists()) {
                Files.delete(smokeApiConfig.toPath())
                logger.debug("Removed SmokeAPI.config.json from "+gameDirectory.absolutePath)
            }
            val koaloaderLog = File(gameDirectory, "Koaloader.log")
            if (koaloaderLog.exists()) {
                Files.delete(koaloaderLog.toPath())
                logger.debug("Removed Koaloader.log from "+gameDirectory.absolutePath)
            }
            val smokeApiLog = File(gameDirectory, "SmokeAPI.log")
            if (smokeApiLog.exists()) {
                Files.delete(smokeApiLog.toPath())
                logger.debug("Removed SmokeAPI.log from "+gameDirectory.absolutePath)
            }
            val uplayR1Name = if (isa == ISA.X86_64) "uplay_r164.dll" else "uplay_r1.dll"
            val uplayR1OriginalName = if (isa == ISA.X86_64) "uplay_r164_o.dll" else "uplay_r1_o.dll"
            val uplayR2Name = if (isa == ISA.X86_64) "uplay_r264.dll" else "uplay_r2.dll"
            val uplayR2OriginalName = if (isa == ISA.X86_64) "uplay_r264_o.dll" else "uplay_r2_o.dll"
            val uplayR1LoaderName = if (isa == ISA.X86_64) "uplay_r1_loader64.dll" else "uplay_r1_loader.dll"
            val uplayR2LoaderName = if (isa == ISA.X86_64) "uplay_r2_loader64.dll" else "uplay_r2_loader.dll"
            val uplayR1 = File(gameDirectory, uplayR1Name)
            val uplayR1Original = File(gameDirectory, uplayR1OriginalName)
            if (uplayR1Original.exists()) {
                Files.move(uplayR1Original.toPath(), uplayR1.toPath(), StandardCopyOption.REPLACE_EXISTING)
                logger.debug("Restored original $uplayR1Name from "+gameDirectory.absolutePath)
            } else {
                if (uplayR1.exists()) {
                    Files.delete(uplayR1.toPath())
                    logger.debug("Removed $uplayR1Name from "+gameDirectory.absolutePath)
                }
            }
            val uplayR2 = File(gameDirectory, uplayR2Name)
            val uplayR2Original = File(gameDirectory, uplayR2OriginalName)
            if (uplayR2Original.exists()) {
                Files.move(uplayR2Original.toPath(), uplayR2.toPath(), StandardCopyOption.REPLACE_EXISTING)
                logger.debug("Restored original $uplayR2Name from "+gameDirectory.absolutePath)
            }
            return UninstallResult.Success
        } catch (e: Exception) {
            logger.error(e, "Error uninstalling proxy mode")
            return UninstallResult.Error("Failed to uninstall proxy mode: ${e.message}")
        }
    }
} 
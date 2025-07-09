package acidicoala.koalageddon.core.use_case

import acidicoala.koalageddon.core.model.KoalaTool
import acidicoala.koalageddon.core.model.ISA
import acidicoala.koalageddon.core.logging.AppLogger
import acidicoala.koalageddon.core.model.AppPaths
import acidicoala.koalageddon.core.use_case.GameInstallationStatus
import acidicoala.koalageddon.uplay.domain.use_case.UninstallUplayGameUnlocker
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import kotlinx.coroutines.runBlocking

class InstallUplayGameUnlocker(override val di: DI) : DIAware {
    sealed class InstallationMode {
        object Proxy : InstallationMode()
        object Hook : InstallationMode()
    }
    sealed class InstallationResult {
        object Success : InstallationResult()
        data class Error(val message: String) : InstallationResult()
    }

    private val paths: AppPaths by instance()
    private val logger: AppLogger by instance()
    private val downloadAndCacheKoalaTool: DownloadAndCacheKoalaTool by instance()
    private val detectGameArchitecture: DetectGameArchitecture by instance()
    private val detectInstallationStatus: DetectInstallationStatus by instance()
    private val uninstallUplayGameUnlocker: UninstallUplayGameUnlocker by instance()

    operator fun invoke(
        gameExecutablePath: String,
        mode: InstallationMode
    ): InstallationResult {
        return try {
            val gameFile = File(gameExecutablePath)
            if (!gameFile.exists()) {
                logger.error("Game executable not found: $gameExecutablePath")
                return InstallationResult.Error("Game executable not found: $gameExecutablePath")
            }
            val gameDirectory = gameFile.parentFile
            val isa = detectGameArchitecture(gameFile)

            val currentStatus = detectInstallationStatus(gameDirectory)
            if (currentStatus != GameInstallationStatus.NotInstalled) {
                logger.debug("Uninstalling current installation before switching modes")
                val uninstallResult = uninstallUplayGameUnlocker(gameExecutablePath)
                if (uninstallResult is acidicoala.koalageddon.uplay.domain.use_case.UninstallUplayGameUnlocker.UninstallResult.Error) {
                    logger.error("Failed to uninstall current mode: ${uninstallResult.message}")
                    return InstallationResult.Error("Failed to uninstall current mode: ${uninstallResult.message}")
                }
            }

            runBlocking { 
                downloadAndCacheKoalaTool(KoalaTool.SleepAPI)
                downloadAndCacheKoalaTool(KoalaTool.Koaloader)
            }
            val sleepApiAsset = findCachedAsset(KoalaTool.SleepAPI.name)
            val koaloaderAsset = findCachedAsset(KoalaTool.Koaloader.name)
            if (sleepApiAsset == null) {
                logger.error("Failed to find cached SleepAPI asset")
                return InstallationResult.Error("Failed to find cached SleepAPI asset")
            }
            if (koaloaderAsset == null) {
                logger.error("Failed to find cached Koaloader asset")
                return InstallationResult.Error("Failed to find cached Koaloader asset")
            }

            when (mode) {
                is InstallationMode.Proxy -> installProxyMode(gameDirectory, isa, sleepApiAsset, koaloaderAsset)
                is InstallationMode.Hook -> installHookMode(gameDirectory, isa, sleepApiAsset)
            }
        } catch (e: Exception) {
            logger.error(e, "Installation failed")
            InstallationResult.Error("Installation failed: ${e.message}")
        }
    }

    private fun installProxyMode(gameDirectory: File, isa: ISA, sleepApiAsset: File, koaloaderAsset: File): InstallationResult {
        try {
            val versionDll = File(gameDirectory, "version.dll")
            if (versionDll.exists()) {
                try {
                    Files.delete(versionDll.toPath())
                    logger.debug("Removed leftover version.dll before installing Proxy mode")
                } catch (e: Exception) {
                    logger.warn("Failed to remove leftover version.dll: ${e.message}")
                }
            }
            val subfolder = if (isa == ISA.X86_64) "version-64" else "version-32"
            val dllPath = "$subfolder/version.dll"
            extractDllFromZip(koaloaderAsset, dllPath, File(gameDirectory, "version.dll"))
            extractDllFromZip(sleepApiAsset, "uplay_r164.dll", File(gameDirectory, "SleepAPI64.dll"))
            return InstallationResult.Success
        } catch (e: Exception) {
            logger.error(e, "Failed to install proxy mode")
            return InstallationResult.Error("Failed to install proxy mode: ${e.message}")
        }
    }

    private fun installHookMode(gameDirectory: File, isa: ISA, sleepApiAsset: File): InstallationResult {
        logger.warn("Hook mode installation for SleepAPI is under development.")
        return InstallationResult.Error("Hook mode installation for SleepAPI is under development.")
    }

    private fun findCachedAsset(toolName: String): File? {
        val cacheDir = paths.cacheDir.toFile()
        return cacheDir.listFiles()?.find { file ->
            file.name.contains(toolName, ignoreCase = true) && 
            (file.extension.equals("zip", ignoreCase = true))
        }
    }

    private fun extractDllFromZip(zipFile: File, dllName: String, targetFile: File) {
        logger.debug("Extracting $dllName from ${zipFile.name} to ${targetFile.name}")
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            var found = false
            while (entry != null) {
                logger.debug("Checking zip entry: ${entry.name}")
                if (entry.name.endsWith(dllName, ignoreCase = true)) {
                    logger.debug("Found matching entry: ${entry.name}")
                    Files.copy(zip, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    found = true
                    break
                }
                entry = zip.nextEntry
            }
            if (!found) {
                logger.error("Entry $dllName not found in zip file ${zipFile.name}")
                throw IllegalStateException("Entry $dllName not found in zip file ${zipFile.name}")
            }
        }
    }
} 
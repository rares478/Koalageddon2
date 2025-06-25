package acidicoala.koalageddon.core.use_case

import acidicoala.koalageddon.core.model.KoalaTool
import acidicoala.koalageddon.core.model.Store
import acidicoala.koalageddon.core.model.ISA
import acidicoala.koalageddon.core.model.AppPaths
import acidicoala.koalageddon.core.logging.AppLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import kotlinx.coroutines.runBlocking

class InstallSteamGameUnlocker(override val di: DI) : DIAware {
    
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
            
            when (mode) {
                is InstallationMode.Proxy -> installProxyMode(gameDirectory, isa)
                is InstallationMode.Hook -> installHookMode(gameDirectory, isa)
            }
            
            InstallationResult.Success
        } catch (e: Exception) {
            logger.error(e, "Installation failed")
            InstallationResult.Error("Installation failed: ${e.message}")
        }
    }
    
    private fun installProxyMode(gameDirectory: File, isa: ISA): InstallationResult {
        val versionDll = File(gameDirectory, "version.dll")
        if (versionDll.exists()) {
            try {
                Files.delete(versionDll.toPath())
                logger.debug("Removed leftover version.dll before installing Proxy mode")
            } catch (e: Exception) {
                logger.warn("Failed to remove leftover version.dll: ${e.message}")
            }
        }
        
        val steamApiName = if (isa == ISA.X86_64) "steam_api64.dll" else "steam_api.dll"
        val steamApiFile = File(gameDirectory, steamApiName)
        val steamApiOriginalName = if (isa == ISA.X86_64) "steam_api64_o.dll" else "steam_api_o.dll"
        val steamApiOriginalFile = File(gameDirectory, steamApiOriginalName)

        if (!steamApiFile.exists()) {
            logger.error("$steamApiName not found in game directory: ${gameDirectory.absolutePath}")
            return InstallationResult.Error("$steamApiName not found in game directory")
        }

        if (!steamApiOriginalFile.exists()) {
            Files.move(steamApiFile.toPath(), steamApiOriginalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        val smokeApiResult = downloadAndExtractSmokeAPI(gameDirectory, isa)
        if (smokeApiResult is InstallationResult.Error) {
            logger.error("Failed to download/extract SmokeAPI: ${smokeApiResult.message}")
            return smokeApiResult
        }

        val smokeApiFile = File(gameDirectory, "SmokeAPI.dll")
        val targetFile = File(gameDirectory, steamApiName)
        Files.move(smokeApiFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        
        return InstallationResult.Success
    }
    
    private fun installHookMode(gameDirectory: File, isa: ISA): InstallationResult {
        // Download Koaloader
        val koaloaderResult = downloadKoaloader(gameDirectory, isa)
        if (koaloaderResult is InstallationResult.Error) {
            logger.error("Failed to download Koaloader: ${koaloaderResult.message}")
            return koaloaderResult
        }
        
        // Download and place SmokeAPI
        val smokeApiResult = downloadAndExtractSmokeAPI(gameDirectory, isa)
        if (smokeApiResult is InstallationResult.Error) {
            logger.error("Failed to download/extract SmokeAPI: ${smokeApiResult.message}")
            return smokeApiResult
        }
        
        return InstallationResult.Success
    }
    
    private fun downloadKoaloader(gameDirectory: File, isa: ISA): InstallationResult {
        return try {
            runBlocking {
                downloadAndCacheKoalaTool(KoalaTool.Koaloader)
            }

            val cachedAsset = findCachedAsset(KoalaTool.Koaloader.name)
            if (cachedAsset == null) {
                logger.error("Failed to find cached Koaloader asset")
                return InstallationResult.Error("Failed to find cached Koaloader asset")
            }

            val subfolder = if (isa == ISA.X86_64) "version-64" else "version-32"
            val dllPath = "$subfolder/version.dll"
            
            extractDllFromZip(cachedAsset, dllPath, File(gameDirectory, "version.dll"))
            
            InstallationResult.Success
        } catch (e: Exception) {
            logger.error(e, "Failed to download Koaloader")
            InstallationResult.Error("Failed to download Koaloader: ${e.message}")
        }
    }
    
    private fun downloadAndExtractSmokeAPI(gameDirectory: File, isa: ISA): InstallationResult {
        return try {
            runBlocking {
                downloadAndCacheKoalaTool(KoalaTool.SmokeAPI)
            }

            val cachedAsset = findCachedAsset(KoalaTool.SmokeAPI.name)
            if (cachedAsset == null) {
                logger.error("Failed to find cached SmokeAPI asset")
                return InstallationResult.Error("Failed to find cached SmokeAPI asset")
            }

            val steamApiDllName = if (isa == ISA.X86_64) "steam_api64.dll" else "steam_api.dll"

            extractDllFromZip(cachedAsset, steamApiDllName, File(gameDirectory, "SmokeAPI.dll"))

            extractDllFromZip(cachedAsset, "SmokeAPI.config.json", File(gameDirectory, "SmokeAPI.config.json"))
            
            InstallationResult.Success
        } catch (e: Exception) {
            logger.error(e, "Failed to download SmokeAPI")
            InstallationResult.Error("Failed to download SmokeAPI: ${e.message}")
        }
    }
    
    private fun findCachedAsset(toolName: String): File? {
        val cacheDir = paths.cacheDir.toFile()
        return cacheDir.listFiles()?.find { file ->
            file.name.contains(toolName, ignoreCase = true) && 
            (file.extension.equals("zip", ignoreCase = true) || file.extension.equals("7z", ignoreCase = true))
        }
    }
    
    private fun extractDllFromZip(zipFile: File, dllName: String, targetFile: File) {
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(dllName, ignoreCase = true)) {
                    Files.copy(zip, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    break
                }
                entry = zip.nextEntry
            }
        }
    }
} 
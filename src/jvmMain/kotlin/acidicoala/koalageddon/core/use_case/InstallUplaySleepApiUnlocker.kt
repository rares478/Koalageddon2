package acidicoala.koalageddon.core.use_case

import acidicoala.koalageddon.core.io.appJson
import acidicoala.koalageddon.core.logging.AppLogger
import acidicoala.koalageddon.core.model.AppPaths
import acidicoala.koalageddon.core.model.ILangString
import acidicoala.koalageddon.core.model.KoalaTool
import acidicoala.koalageddon.core.model.LangString
import acidicoala.koalageddon.core.model.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.div
import kotlin.io.path.writeText
import java.io.File

class InstallUplaySleepApiUnlocker(override val di: DI) : DIAware {
    private val paths: AppPaths by instance()
    private val logger: AppLogger by instance()
    private val downloadAndCacheKoalaTool: DownloadAndCacheKoalaTool by instance()
    private val unzipToolDll: UnzipToolDll by instance()
    private val forceCloseProcess: ForceCloseProcess by instance()
    private val isProcessRunning: IsProcessRunning by instance()

    private val sleepApiUnlocker get() = KoalaTool.SleepAPI
    private val koaloader get() = KoalaTool.Koaloader
    private val koalinjector get() = KoalaTool.Koalinjector

    private fun findCachedAsset(toolName: String): File? {
        val cacheDir = paths.cacheDir.toFile()
        return cacheDir.listFiles()?.find { file ->
            file.name.contains(toolName, ignoreCase = true) &&
            (file.extension.equals("zip", ignoreCase = true) || file.extension.equals("7z", ignoreCase = true))
        }
    }

    suspend fun install(store: Store = Store.Ubisoft): Flow<ILangString> = channelFlow {
        try {
            closeIfRunning(store)
            
            send(LangString("%0" to koaloader.name) { downloadingRelease })
            downloadAndCacheKoalaTool(koaloader).collect { send(it) }
            send(LangString("%0" to sleepApiUnlocker.name) { downloadingRelease })
            downloadAndCacheKoalaTool(sleepApiUnlocker).collect { send(it) }
            send(LangString("%0" to koalinjector.name) { downloadingRelease })
            downloadAndCacheKoalaTool(koalinjector).collect { send(it) }

            val sleepApiZip = findCachedAsset(sleepApiUnlocker.name)
                ?: throw IllegalStateException("SleepAPI release zip not found in cache")
            val koalinjectorZip = findCachedAsset(koalinjector.name)
                ?: throw IllegalStateException("Koalinjector release zip not found in cache")
            val koaloaderZip = findCachedAsset(koaloader.name)
                ?: throw IllegalStateException("Koaloader release zip not found in cache")

            val koaloaderDllPath = paths.getKoaloaderDll(store)
            logger.debug("Extracting version.dll to ${'$'}koaloaderDllPath")
            unzipToolDll(
                tool = koaloader,
                entry = "${koaloader.originalName}-32/${koaloader.originalName}.dll",
                destination = koaloaderDllPath,
                zipOverride = koaloaderZip
            )

            val sleepApiDir = paths.getUnlockerDll(sleepApiUnlocker).parent
            Files.createDirectories(sleepApiDir)
            val sleepApiDllPath = sleepApiDir / "SleepAPI.dll"
            val sleepApi64DllPath = sleepApiDir / "SleepAPI64.dll"
            val koalinjectorExePath = sleepApiDir / "Koalinjector.exe"
            logger.debug("Extracting uplay_r1.dll to ${'$'}sleepApiDllPath")
            unzipToolDll(
                tool = sleepApiUnlocker,
                entry = "uplay_r1.dll",
                destination = sleepApiDllPath,
                zipOverride = sleepApiZip
            )
            logger.debug("Extracting uplay_r164.dll to ${'$'}sleepApi64DllPath")
            unzipToolDll(
                tool = sleepApiUnlocker,
                entry = "uplay_r164.dll",
                destination = sleepApi64DllPath,
                zipOverride = sleepApiZip
            )
            logger.debug("Extracting Koalinjector.exe to ${'$'}koalinjectorExePath")
            unzipToolDll(
                tool = koalinjector,
                entry = "Koalinjector.exe",
                destination = koalinjectorExePath,
                zipOverride = koalinjectorZip
            )

            val sleepApiConfigPath = sleepApiDir / "SleepAPI.config.json"
            logger.debug("Writing SleepAPI config to ${'$'}sleepApiConfigPath")
            sleepApiConfigPath.writeText(appJson.encodeToString(KoalaTool.SleepAPI.Config.serializer(), KoalaTool.SleepAPI.defaultConfig))

            val koaloaderConfigPath = paths.getKoaloaderConfig(store)
            val koaloaderConfig = KoalaTool.Koaloader.Config(
                logging = false,
                enabled = true,
                autoLoad = false,
                targets = listOf(store.executable),
                modules = listOf(
                    KoalaTool.Koaloader.Module(
                        path = sleepApiDllPath.toAbsolutePath().toString(),
                        name = "SleepAPI",
                        required = true
                    )
                )
            )
            logger.debug("Writing Koaloader config to ${'$'}koaloaderConfigPath")
            koaloaderConfigPath.writeText(appJson.encodeToString(KoalaTool.Koaloader.Config.serializer(), koaloaderConfig))

            logger.info("Uplay SleepAPI unlocker installed successfully.")
        } catch (e: Exception) {
            logger.error(e, "Failed to install Uplay SleepAPI unlocker.")
            throw e
        }
    }

    suspend fun uninstall(store: Store = Store.Ubisoft) = withContext(Dispatchers.IO) {
        try {
            closeIfRunning(store)
            
            val koaloaderDllPath = paths.getKoaloaderDll(store)
            if (koaloaderDllPath.exists()) {
                logger.debug("Deleting version.dll at ${'$'}koaloaderDllPath")
                koaloaderDllPath.deleteIfExists()
            }

            val sleepApiDir = paths.getUnlockerDll(sleepApiUnlocker).parent
            val sleepApiDllPath = sleepApiDir / "SleepAPI.dll"
            val sleepApi64DllPath = sleepApiDir / "SleepAPI64.dll"
            val koalinjectorExePath = sleepApiDir / "Koalinjector.exe"
            val sleepApiConfigPath = sleepApiDir / "SleepAPI.config.json"
            if (sleepApiDllPath.exists()) {
                logger.debug("Deleting SleepAPI.dll at ${'$'}sleepApiDllPath")
                sleepApiDllPath.deleteIfExists()
            }
            if (sleepApi64DllPath.exists()) {
                logger.debug("Deleting SleepAPI64.dll at ${'$'}sleepApi64DllPath")
                sleepApi64DllPath.deleteIfExists()
            }
            if (koalinjectorExePath.exists()) {
                logger.debug("Deleting Koalinjector.exe at ${'$'}koalinjectorExePath")
                koalinjectorExePath.deleteIfExists()
            }
            if (sleepApiConfigPath.exists()) {
                logger.debug("Deleting SleepAPI.config.json at ${'$'}sleepApiConfigPath")
                sleepApiConfigPath.deleteIfExists()
            }

            val koaloaderConfigPath = paths.getKoaloaderConfig(store)
            if (koaloaderConfigPath.exists()) {
                logger.debug("Deleting Koaloader config at ${'$'}koaloaderConfigPath")
                koaloaderConfigPath.deleteIfExists()
            }

            logger.info("Uplay SleepAPI unlocker uninstalled successfully.")
        } catch (e: Exception) {
            logger.error(e, "Failed to uninstall Uplay SleepAPI unlocker.")
            throw e
        }
    }

    private suspend fun closeIfRunning(store: Store) {
        paths.getStoreExecutablePath(store).let { processPath ->
            if (isProcessRunning(processPath)) {
                forceCloseProcess(processPath)
            }
        }
    }
} 
package acidicoala.koalageddon.core.use_case

import acidicoala.koalageddon.core.io.appJson
import acidicoala.koalageddon.core.model.*
import acidicoala.koalageddon.core.model.KoalaTool.Koaloader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.encodeToStream
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import kotlin.io.path.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

class ModifyInstallationStatus(override val di: DI) : DIAware {
    private val paths: AppPaths by instance()
    private val downloadAndCacheKoalaTool: DownloadAndCacheKoalaTool by instance()
    private val unzipToolDll: UnzipToolDll by instance()
    private val forceCloseProcess: ForceCloseProcess by instance()
    private val isProcessRunning: IsProcessRunning by instance()

    suspend operator fun invoke(
        store: Store,
        currentStatus: InstallationStatus
    ): Flow<ILangString> = when (currentStatus) {
        is InstallationStatus.Installed -> uninstall(store)
        is InstallationStatus.NotInstalled -> install(store)
        is InstallationStatus.Updating -> channelFlow {}
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun install(store: Store) = channelFlow {
        closeIfRunning(store)

        val koaloader = Koaloader

        downloadAndCacheKoalaTool(koaloader).collect(::send)

        downloadAndCacheKoalaTool(store.unlocker).collect(::send)

        store.additionalUnlockers.forEach { unlocker ->
            downloadAndCacheKoalaTool(unlocker).collect(::send)
        }

        unzipToolDll(
            tool = koaloader,
            entry = "${koaloader.originalName}-${store.isa.bitness}/${koaloader.originalName}.dll",
            destination = paths.getKoaloaderDll(store),
        )

        val modules = mutableListOf<Koaloader.Module>()
        
        val mainUnlockerDir = paths.getUnlockerDll(store.unlocker).parent
        val mainUnlockerName = generateRandomName()
        modules.add(
            Koaloader.Module(
                path = (mainUnlockerDir / "$mainUnlockerName.dll").absolutePathString(),
                name = mainUnlockerName
            )
        )
        
        store.additionalUnlockers.forEach { unlocker ->
            val unlockerDir = paths.getUnlockerDll(unlocker).parent
            val unlockerName = generateRandomName()
            modules.add(
                Koaloader.Module(
                    path = (unlockerDir / "$unlockerName.dll").absolutePathString(),
                    name = unlockerName
                )
            )
        }

        val koaloaderConfig = Koaloader.Config(
            autoLoad = false,
            targets = listOf(store.executable),
            modules = modules
        )

        appJson.encodeToStream(koaloaderConfig, paths.getKoaloaderConfig(store).outputStream())

        unzipToolDll(
            tool = store.unlocker,
            entry = "${store.unlocker.originalName}${store.isa.bitnessSuffix}.dll",
            destination = mainUnlockerDir / "$mainUnlockerName.dll",
        )

        store.additionalUnlockers.forEachIndexed { index, unlocker ->
            val unlockerDir = paths.getUnlockerDll(unlocker).parent
            val unlockerName = modules[index + 1].name // +1 because index 0 is main unlocker
            unzipToolDll(
                tool = unlocker,
                entry = "${unlocker.originalName}${store.isa.bitnessSuffix}.dll",
                destination = unlockerDir / "$unlockerName.dll",
            )
        }

        try {
            store.unlocker.parseConfig(paths.getUnlockerConfig(store.unlocker))
        } catch (e: Exception) {
            store.unlocker.writeConfig(path = paths.getUnlockerConfig(store.unlocker), store.unlocker.defaultConfig)
        }
        
        store.additionalUnlockers.forEach { unlocker ->
            try {
                unlocker.parseConfig(paths.getUnlockerConfig(unlocker))
            } catch (e: Exception) {
                unlocker.writeConfig(path = paths.getUnlockerConfig(unlocker), unlocker.defaultConfig)
            }
        }
    }

    private fun generateRandomName(): String {
        return (1..8)
            .map { ThreadLocalRandom.current().nextInt(0, 36) }
            .map { if (it < 10) it + 48 else it + 87 }
            .map { it.toChar() }
            .joinToString("")
    }

    private fun uninstall(store: Store) = channelFlow<ILangString> {
        closeIfRunning(store)

        val koaloaderConfigPath = paths.getKoaloaderConfig(store)

        if (koaloaderConfigPath.exists()) {
            val koaloaderConfig = appJson.decodeFromString<Koaloader.Config>(koaloaderConfigPath.readText())
            koaloaderConfig.modules.forEach { module ->
                Path(module.path).resolve("${module.name}.dll").deleteIfExists()
            }
            koaloaderConfigPath.deleteIfExists()
        }

        paths.getKoaloaderDll(store).deleteIfExists()
    }

    private suspend fun closeIfRunning(store: Store) {
        paths.getStoreExecutablePath(store).let { processPath ->
            if (isProcessRunning(processPath)) {
                forceCloseProcess(processPath)
            }
        }
    }
}
package acidicoala.koalageddon.core.model

import acidicoala.koalageddon.core.io.appJson
import acidicoala.koalageddon.core.values.Strings
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
sealed class KoalaTool(
    val name: String,
    val originalName: String,
    val majorVersion: Int
) {
    val configName = "$name.config.json"
    val logName = "$name.log.log"
    val homePage = "https://github.com/rares478/$name#readme"
    val gitHubReleaseUrl = "https://api.github.com/repos/rares478/$name/releases"

    interface IConfig

    abstract val defaultConfig: IConfig

    abstract fun parseConfig(path: Path): IConfig

    abstract fun writeConfig(path: Path, config: IConfig)

    abstract fun writeDefaultConfig(path: Path)

    object Koaloader : KoalaTool(name = "Koaloader", originalName = "version", majorVersion = 3) {
        @Serializable
        data class Module(
            val path: String = "",
            val name: String = "",
            val required: Boolean = true,
        )

        @Serializable
        data class Config(
            val logging: Boolean = false,
            val enabled: Boolean = true,
            @SerialName("auto_load") val autoLoad: Boolean = true,
            val targets: List<String> = listOf(),
            val modules: List<Module> = listOf(),
        ) : IConfig

        override val defaultConfig = Config()

        override fun parseConfig(path: Path) = appJson.decodeFromStream<Config>(path.inputStream())

        override fun writeConfig(path: Path, config: IConfig) =
            appJson.encodeToStream(config as Config, path.outputStream())

        override fun writeDefaultConfig(path: Path) = appJson.encodeToStream(Config(), path.outputStream())
    }

    object SmokeAPI : KoalaTool(name = "SmokeAPI", originalName = "steam_api", majorVersion = 2) {
        @Serializable
        enum class AppStatus : ILangString {
            @SerialName("original")
            Original {
                override fun text(strings: Strings) = strings.appStatusOriginal
            },

            @SerialName("unlocked")
            Unlocked {
                override fun text(strings: Strings) = strings.appStatusUnlocked
            },

            @SerialName("locked")
            Locked {
                override fun text(strings: Strings) = strings.appStatusLocked
            };

            companion object {
                val validAppStatuses = arrayOf(Original, Unlocked)
                val validDlcStatuses = arrayOf(Original, Unlocked, Locked)
            }
        }

        @Serializable
        data class App(
            val dlcs: Map<String, String> = mapOf()
        )

        @Serializable
        data class Config(
            val logging: Boolean = false,
            @SerialName("\$version") val version: Int = 2,
            @SerialName("unlock_family_sharing") val unlockFamilySharing: Boolean = true,
            @SerialName("default_app_status") val defaultAppStatus: AppStatus = AppStatus.Unlocked,
            @SerialName("override_app_status") val overrideAppStatus: Map<String, AppStatus> = mapOf(),
            @SerialName("override_dlc_status") val overrideDlcStatus: Map<String, AppStatus> = mapOf(),
            @SerialName("auto_inject_inventory") val autoInjectInventory: Boolean = true,
            @SerialName("extra_inventory_items") val extraInventoryItems: List<Int> = listOf(),
            @SerialName("store_config") val storeConfig: JsonObject? = null,
            @SerialName("extra_dlcs") val extraDlcs: Map<String, App> = mapOf(),
        ) : IConfig

        override val defaultConfig = Config()

        override fun parseConfig(path: Path) = appJson.decodeFromStream<Config>(path.inputStream())

        override fun writeConfig(path: Path, config: IConfig) =
            appJson.encodeToStream(config as Config, path.outputStream())

        override fun writeDefaultConfig(path: Path) = appJson.encodeToStream(Config(), path.outputStream())
    }

    object SleepAPI : KoalaTool(name = "SleepAPI", originalName = "uplay_r1", majorVersion = 1) {
        @Serializable
        data class Dlc(val ProductID: Int, val name: String)
        @Serializable
        data class Item(val ProductID: Int, val name: String)
        @Serializable
        data class Config(
            val version: Int = 1,
            val general: General = General(),
            val r1: R1 = R1(),
            val r2: R2 = R2()
        ) : IConfig {
            @Serializable
            data class General(val logging: Boolean = true)
            @Serializable
            data class R1(val lang: String = "default", val hook_loader: Boolean = false, val blacklist: List<String> = emptyList())
            @Serializable
            data class R2(
                val lang: String = "default",
                val hook_loader: Boolean = false,
                val auto_fetch: Boolean = true,
                val dlcs: List<Dlc> = emptyList(),
                val items: List<Item> = emptyList(),
                val blacklist: List<String> = emptyList()
            )
        }

        override val defaultConfig = Config()

        override fun parseConfig(path: Path) = appJson.decodeFromStream<Config>(path.inputStream())

        override fun writeConfig(path: Path, config: IConfig) =
            appJson.encodeToStream(config as Config, path.outputStream())

        override fun writeDefaultConfig(path: Path) = appJson.encodeToStream(Config(), path.outputStream())
    }

    object Koalinjector : KoalaTool(name = "Koalinjector", originalName = "Koalinjector", majorVersion = 1) {
        @Serializable
        data class Config(val dummy: Boolean = true) : IConfig

        override val defaultConfig = Config()

        override fun parseConfig(path: Path) = appJson.decodeFromStream<Config>(path.inputStream())

        override fun writeConfig(path: Path, config: IConfig) =
            appJson.encodeToStream(config as Config, path.outputStream())

        override fun writeDefaultConfig(path: Path) = appJson.encodeToStream(Config(), path.outputStream())
    }
}
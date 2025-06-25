package acidicoala.koalageddon.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.io.File

@Serializable
data class HookedGameEntry(
    val gameName: String,
    val architecture: String,
    val path: String,
    val method: String // "Proxy" or "Hook"
)

class HookedGamesCache(override val di: DI) : DIAware {
    private val paths: AppPaths by instance()
    private val json = Json { prettyPrint = true }
    private val cacheFile: File get() = paths.log.parent.resolve("hooked_games.json").toFile()

    fun read(): List<HookedGameEntry> {
        if (!cacheFile.exists()) return emptyList()
        return try {
            json.decodeFromString(cacheFile.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun write(entries: List<HookedGameEntry>) {
        cacheFile.writeText(json.encodeToString(entries))
    }

    fun addOrUpdate(entry: HookedGameEntry) {
        val entries = read().toMutableList()
        val idx = entries.indexOfFirst { it.path.equals(entry.path, ignoreCase = true) }
        if (idx >= 0) {
            entries[idx] = entry
        } else {
            entries.add(entry)
        }
        write(entries)
    }

    fun removeByPath(path: String) {
        val entries = read().filterNot { it.path.equals(path, ignoreCase = true) }
        write(entries)
    }
} 
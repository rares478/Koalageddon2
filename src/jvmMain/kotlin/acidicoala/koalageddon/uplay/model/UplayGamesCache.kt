package acidicoala.koalageddon.uplay.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.io.File
import acidicoala.koalageddon.core.model.AppPaths

@Serializable
data class CachedUplayGame(
    val gameName: String,
    val architecture: String,
    val path: String
)

class UplayGamesCache(override val di: DI) : DIAware {
    private val paths: AppPaths by instance()
    private val json = Json { prettyPrint = true }
    private val cacheFile: File get() = paths.log.parent.resolve("uplay_games.json").toFile()

    fun read(): List<CachedUplayGame> {
        if (!cacheFile.exists()) return emptyList()
        return try {
            json.decodeFromString(cacheFile.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun write(entries: List<CachedUplayGame>) {
        cacheFile.writeText(json.encodeToString(entries))
    }

    fun addOrUpdate(entry: CachedUplayGame) {
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
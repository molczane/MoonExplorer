package org.jetbrains.moonexplorer.domain

import kotlinx.serialization.json.Json
import moonexplorer.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Bundled catalog of named Moon sites. T203 / 01-app-shell.
 *
 * `loadBundled()` reads `composeResources/files/sites.json` once at app startup; `search` +
 * `byId` are the only runtime entry points. Search is case-insensitive substring match on
 * `name` and (if present) `subtitle`, results sorted alphabetically by `name`, capped at the
 * caller's `limit`. Trivially cheap for our 16-entry catalog — no indexing needed.
 *
 * Schema is forgiving (`ignoreUnknownKeys = true`) so future fields don't break the runtime
 * parser if a build pipeline adds e.g. accent metadata or rendering hints.
 */
class SiteCatalog(private val sites: List<MoonSite>) {

    /** All catalog entries in their bundled order. */
    val all: List<MoonSite> get() = sites

    fun search(query: String, limit: Int = 10): List<MoonSite> {
        val q = query.trim().lowercase()
        if (q.isEmpty() || limit <= 0) return emptyList()
        return sites
            .asSequence()
            .filter { matches(it, q) }
            .sortedBy { it.name }
            .take(limit)
            .toList()
    }

    fun byId(id: String): MoonSite? = sites.firstOrNull { it.id == id }

    private fun matches(site: MoonSite, lowercaseQuery: String): Boolean =
        lowercaseQuery in site.name.lowercase() ||
            site.subtitle?.lowercase()?.contains(lowercaseQuery) == true

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @OptIn(ExperimentalResourceApi::class)
        suspend fun loadBundled(): SiteCatalog {
            val text = Res.readBytes(BUNDLED_PATH).decodeToString()
            val parsed = json.decodeFromString<List<MoonSite>>(text)
            return SiteCatalog(parsed)
        }

        private const val BUNDLED_PATH = "files/sites.json"
    }
}

package org.jetbrains.moonexplorer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T204 — exercises [SiteCatalog]'s search + byId logic against a hardcoded sample. The
 * `loadBundled()` factory's compose-resources read isn't covered here for the same reason
 * `MoonAssetLoaderTest` (T143) was deferred — `Res.readBytes` in `commonTest` on AGP 9 alpha
 * needs more setup than this spec wants to invest. The real `sites.json` is exercised
 * end-to-end by the running app (FR-001).
 */
class SiteCatalogTest {

    private val catalog = SiteCatalog(SAMPLE_SITES)

    @Test
    fun search_findsByName() {
        val results = catalog.search("tych")
        assertEquals(1, results.size)
        assertEquals("tycho", results[0].id)
    }

    @Test
    fun search_findsBySubtitle() {
        // "Sea of Tranquility" is the subtitle of mare_tranquillitatis; the name is "Mare Tranquillitatis".
        val results = catalog.search("tranquility")
        assertEquals(1, results.size)
        assertEquals("mare_tranquillitatis", results[0].id)
    }

    @Test
    fun search_isCaseInsensitive() {
        val lower = catalog.search("mare")
        val upper = catalog.search("MARE")
        val mixed = catalog.search("MaRe")
        assertEquals(lower.map { it.id }, upper.map { it.id })
        assertEquals(lower.map { it.id }, mixed.map { it.id })
    }

    @Test
    fun search_emptyQueryReturnsEmpty() {
        assertTrue(catalog.search("").isEmpty())
    }

    @Test
    fun search_whitespaceQueryReturnsEmpty() {
        assertTrue(catalog.search("   ").isEmpty())
    }

    @Test
    fun search_resultsAreSortedAlphabeticallyByName() {
        val results = catalog.search("mare").map { it.name }
        assertEquals(results.sorted(), results, "expected alphabetical-by-name order, got $results")
    }

    @Test
    fun search_respectsLimit() {
        val limited = catalog.search("mare", limit = 2)
        assertEquals(2, limited.size)
    }

    @Test
    fun search_zeroOrNegativeLimitReturnsEmpty() {
        assertTrue(catalog.search("mare", limit = 0).isEmpty())
        assertTrue(catalog.search("mare", limit = -1).isEmpty())
    }

    @Test
    fun byId_returnsMatch() {
        val site = catalog.byId("apollo_11")
        assertNotNull(site)
        assertEquals("Apollo 11", site.name)
    }

    @Test
    fun byId_returnsNullForMissing() {
        assertNull(catalog.byId("does-not-exist"))
    }

    @Test
    fun all_returnsBundledOrder() {
        // Order should match the constructor input — used by the UI's "browse all" affordances later.
        assertEquals(SAMPLE_SITES.map { it.id }, catalog.all.map { it.id })
    }
}

private val SAMPLE_SITES: List<MoonSite> = listOf(
    MoonSite(
        id = "tycho",
        name = "Tycho",
        subtitle = null,
        lat = -43.31, lon = -11.36,
        type = SiteType.CRATER,
        description = "Bright ray crater.",
    ),
    MoonSite(
        id = "mare_tranquillitatis",
        name = "Mare Tranquillitatis",
        subtitle = "Sea of Tranquility",
        lat = 8.5, lon = 31.4,
        type = SiteType.MARE,
        description = "Apollo 11 landing region.",
    ),
    MoonSite(
        id = "mare_serenitatis",
        name = "Mare Serenitatis",
        subtitle = "Sea of Serenity",
        lat = 28.0, lon = 17.0,
        type = SiteType.MARE,
        description = "Round dark basin.",
    ),
    MoonSite(
        id = "mare_imbrium",
        name = "Mare Imbrium",
        subtitle = "Sea of Showers",
        lat = 32.8, lon = -15.6,
        type = SiteType.MARE,
        description = "Largest visible mare.",
    ),
    MoonSite(
        id = "apollo_11",
        name = "Apollo 11",
        subtitle = "Tranquillity Base",
        lat = 0.67, lon = 23.47,
        type = SiteType.LANDING_SITE,
        description = "First crewed lunar landing, 1969.",
    ),
)

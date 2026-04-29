package org.jetbrains.moonexplorer.assets

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T140 — round-trip a known-good manifest JSON. The shape matches what `manifest.py`
 * produces; if either side drifts, the runtime parse breaks. Lock both sides here.
 */
class AssetManifestTest {

    @Test
    fun parse_extractsVersionAndEntries() {
        val parsed = AssetManifest.parse(MANIFEST_JSON)

        assertEquals("2026-04-29-1", parsed.version)

        assertEquals(
            "https://github.com/molczane/MoonExplorer/releases/download/assets-v1/moon_albedo_8k.ktx2",
            parsed.albedo.url,
        )
        assertEquals(
            "de88747bdea4258e7e3e1699c16a2998047be3aace6197d5de4257794820337f",
            parsed.albedo.sha256,
        )
        assertEquals(4_656_782L, parsed.albedo.sizeBytes)
        assertEquals(8192, parsed.albedo.width)
        assertEquals(4096, parsed.albedo.height)

        assertEquals(
            "https://github.com/molczane/MoonExplorer/releases/download/assets-v1/moon_normal_8k.ktx2",
            parsed.normal.url,
        )
        assertEquals(
            "371d3eaaba05c3727bc9dc510dcd8cf15304e22d3e3027d9cb8a443177725cf4",
            parsed.normal.sha256,
        )
    }

    @Test
    fun assetEntry_fileNameIsLastUrlSegment() {
        val entry = AssetEntry(
            url = "https://example.com/path/with/segments/moon_albedo_8k.ktx2",
            sha256 = "x",
            sizeBytes = 1L,
            width = 1,
            height = 1,
        )
        assertEquals("moon_albedo_8k.ktx2", entry.fileName)
    }

    @Test
    fun parse_ignoresUnknownKeys() {
        // manifest.py may add diagnostic fields (mipLevels, encoder version, etc.) without bumping
        // the schema. The runtime must accept them gracefully — `Json { ignoreUnknownKeys = true }`.
        val withExtras = """
            {
              "version": "2026-04-29-1",
              "encoder": "toktx 4.4.2",
              "albedo": {
                "url": "https://example.com/a.ktx2",
                "sha256": "deadbeef",
                "sizeBytes": 1,
                "width": 1,
                "height": 1,
                "mipLevels": 12
              },
              "normal": {
                "url": "https://example.com/n.ktx2",
                "sha256": "cafef00d",
                "sizeBytes": 1,
                "width": 1,
                "height": 1,
                "supercompression": "zstd"
              }
            }
        """.trimIndent()
        val parsed = AssetManifest.parse(withExtras)
        assertEquals("2026-04-29-1", parsed.version)
        assertEquals("deadbeef", parsed.albedo.sha256)
    }
}

private val MANIFEST_JSON: String = """
    {
      "version": "2026-04-29-1",
      "albedo": {
        "url": "https://github.com/molczane/MoonExplorer/releases/download/assets-v1/moon_albedo_8k.ktx2",
        "sha256": "de88747bdea4258e7e3e1699c16a2998047be3aace6197d5de4257794820337f",
        "sizeBytes": 4656782,
        "width": 8192,
        "height": 4096
      },
      "normal": {
        "url": "https://github.com/molczane/MoonExplorer/releases/download/assets-v1/moon_normal_8k.ktx2",
        "sha256": "371d3eaaba05c3727bc9dc510dcd8cf15304e22d3e3027d9cb8a443177725cf4",
        "sizeBytes": 23292233,
        "width": 8192,
        "height": 4096
      }
    }
""".trimIndent()

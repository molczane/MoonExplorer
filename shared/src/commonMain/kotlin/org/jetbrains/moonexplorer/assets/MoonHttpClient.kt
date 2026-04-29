package org.jetbrains.moonexplorer.assets

import io.ktor.client.HttpClient

/**
 * Platform-default Ktor [HttpClient] for HD asset fetches.
 *
 * Android binds to OkHttp; iOS binds to Darwin. Both install ContentNegotiation + JSON so
 * the Ktor `body<AssetManifest>()` shorthand works on the future remote-manifest path
 * (currently the manifest is bundled — see ADR-0010).
 *
 * Construction is platform-internal so `:androidApp` doesn't need Ktor / engine artefacts on
 * its own classpath.
 */
expect fun createMoonHttpClient(): HttpClient

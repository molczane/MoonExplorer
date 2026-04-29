package org.jetbrains.moonexplorer.assets

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

actual fun createMoonHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) { json() }
}

package org.jetbrains.moonexplorer

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
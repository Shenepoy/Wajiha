package com.wajiha

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
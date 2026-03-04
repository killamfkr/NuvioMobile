package com.nuvio.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
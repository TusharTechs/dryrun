package com.dryrun.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
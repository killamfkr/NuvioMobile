package com.nuvio.app.core.ui

import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache

private const val IMAGE_MEMORY_CACHE_SIZE_FRACTION = 0.15

internal actual fun ImageLoader.Builder.configurePlatformImageLoader(context: PlatformContext): ImageLoader.Builder =
    memoryCache {
        MemoryCache.Builder()
            .maxSizePercent(context, IMAGE_MEMORY_CACHE_SIZE_FRACTION)
            .build()
    }.components {
        if (Build.VERSION.SDK_INT >= 28) {
            add(AnimatedImageDecoder.Factory())
        } else {
            add(GifDecoder.Factory())
        }
    }

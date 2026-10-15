package com.marketdata.desktop.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface ThemeRenderer {
    fun prepareTheme(dark: Boolean): CompletableFuture<Unit>
}

internal class ThemeTransition(initialDark: Boolean) {
    var dark by mutableStateOf(initialDark)
        private set
    val renderers = mutableStateListOf<ThemeRenderer>()

    fun applyImmediately(target: Boolean) {
        dark = target
    }

    suspend fun apply(target: Boolean) {
        // Do not freeze the entire app if an offscreen renderer stops responding.
        try {
            val pending = renderers.toList().map { it.prepareTheme(target) }
            val prepared = withTimeoutOrNull(2_000) {
                for (frame in pending) suspendCancellableCoroutine<Unit> { continuation ->
                    frame.whenComplete { _, failure ->
                        if (failure == null) continuation.resume(Unit)
                        else continuation.resumeWithException(failure)
                    }
                }
                true
            }
            if (prepared != true) System.err.println("Chart theme preparation timed out")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            System.err.println("Chart theme preparation failed: ${failure.message}")
        }
        coroutineContext.ensureActive()
        dark = target
    }
}

internal val LocalThemeTransition = staticCompositionLocalOf<ThemeTransition?> { null }

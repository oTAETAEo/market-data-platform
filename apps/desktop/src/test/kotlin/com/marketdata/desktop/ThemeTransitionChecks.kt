package com.marketdata.desktop

import com.marketdata.desktop.ui.theme.ThemeRenderer
import com.marketdata.desktop.ui.theme.ThemeTransition
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

fun main() = runBlocking {
    class Renderer : ThemeRenderer {
        var pending = CompletableFuture<Unit>()
        override fun prepareTheme(dark: Boolean) = pending
    }
    val transition = ThemeTransition(true)
    val renderer = Renderer()
    transition.renderers.add(renderer)

    val pending = launch(start = CoroutineStart.UNDISPATCHED) { transition.apply(false) }
    check(transition.dark) { "App palette changed before the chart frame was ready" }
    renderer.pending.complete(Unit)
    pending.join()
    check(!transition.dark)

    renderer.pending = CompletableFuture()
    val obsolete = launch(start = CoroutineStart.UNDISPATCHED) { transition.apply(true) }
    obsolete.cancelAndJoin()
    val obsoleteFrame = renderer.pending
    renderer.pending = CompletableFuture.completedFuture(Unit)
    transition.apply(false)
    obsoleteFrame.complete(Unit)
    check(!transition.dark) { "A cancelled transition applied an obsolete theme" }

    renderer.pending = CompletableFuture.failedFuture(IllegalStateException("Expected renderer failure"))
    transition.apply(true)
    check(transition.dark) { "Renderer failure prevented the app theme from switching" }

    renderer.pending = CompletableFuture()
    transition.apply(false)
    check(!transition.dark) { "An unresponsive chart blocked the theme indefinitely" }

    transition.renderers.clear()
    transition.apply(true)
    check(transition.dark) { "A screen without charts did not switch themes" }
    println("Theme transition checks passed: readiness, cancellation, failure, timeout and no-chart fallback")
}

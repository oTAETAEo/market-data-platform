package com.marketdata.desktop.application

import com.marketdata.core.market.MarketInstrument
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class InstrumentSnapshot(
    val exchange: String,
    val instruments: List<MarketInstrument>,
    val fetchedAt: Instant
)

internal class InstrumentCatalog(
    private val fetch: (String) -> CompletableFuture<List<MarketInstrument>>,
    private val clock: Clock = Clock.systemUTC(),
    private val ttl: Duration = Duration.ofMinutes(10)
) {
    private val snapshots = ConcurrentHashMap<String, InstrumentSnapshot>()
    private val mutex = Mutex()

    fun cached(exchange: String): InstrumentSnapshot? = snapshots[exchange]

    fun isFresh(snapshot: InstrumentSnapshot?): Boolean {
        if (snapshot == null) return false
        val age = Duration.between(snapshot.fetchedAt, clock.instant())
        return !age.isNegative && age < ttl
    }

    suspend fun load(exchange: String, force: Boolean = false): InstrumentSnapshot = mutex.withLock {
        require(exchange in setOf("BINANCE", "BYBIT")) { "Unsupported exchange" }
        val cached = cached(exchange)
        if (!force && isFresh(cached)) return@withLock cached!!
        val instruments = suspendCancellableCoroutine<List<MarketInstrument>> { continuation ->
            val request = fetch(exchange)
            continuation.invokeOnCancellation { request.cancel(true) }
            request.whenComplete { result, failure ->
                if (failure == null) continuation.resume(result)
                else continuation.resumeWithException(failure)
            }
        }
        InstrumentSnapshot(exchange, instruments.toList(), clock.instant()).also { snapshots[exchange] = it }
    }
}

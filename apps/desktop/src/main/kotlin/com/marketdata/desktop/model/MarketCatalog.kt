package com.marketdata.desktop.model

import java.util.Locale

internal object MarketCatalog {
    val assets = listOf(
        MarketAsset("BTC", "Bitcoin", "₿"),
        MarketAsset("ETH", "Ethereum", "Ξ"),
        MarketAsset("SOL", "Solana", "S"),
        MarketAsset("XRP", "XRP", "X"),
        MarketAsset("BNB", "BNB", "B"),
        MarketAsset("DOGE", "Dogecoin", "Ð"),
        MarketAsset("AVAX", "Avalanche", "A"),
        MarketAsset("LINK", "Chainlink", "L")
    )

    fun matches(asset: MarketAsset, query: String): Boolean {
        val normalized = normalize(query)
        return asset.symbol.contains(normalized) || asset.name.uppercase(Locale.ROOT).contains(normalized)
    }

    fun fromInput(input: String): MarketAsset? {
        val normalized = normalize(input)
        val ticker = normalized.removeSuffix("USDT")
        if (!ticker.matches(Regex("[A-Z0-9]{2,15}")) || ticker == "USDT") return null
        return assets.firstOrNull { it.ticker == ticker }
            ?: MarketAsset(ticker, ticker, ticker.first().toString())
    }

    private fun normalize(value: String): String = value.trim().uppercase(Locale.ROOT)
        .replace(Regex("[\\s/_-]"), "")
}

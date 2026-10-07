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
        val normalized = query.trim().uppercase(Locale.ROOT).replace("/", "").replace(" ", "")
        return asset.symbol.contains(normalized) || asset.name.uppercase(Locale.ROOT).contains(normalized)
    }
}

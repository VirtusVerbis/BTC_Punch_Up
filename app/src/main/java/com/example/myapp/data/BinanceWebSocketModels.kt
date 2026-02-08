package com.example.myapp.data

import com.google.gson.annotations.SerializedName

// Binance WebSocket Trade Stream
// Stream: btcusdt@trade
data class BinanceTradeStream(
    val e: String,  // Event type
    @SerializedName("E") val eventTime: Long,   // Event time
    val s: String, // Symbol
    val t: Long,   // Trade ID
    val p: String, // Price
    val q: String, // Quantity
    val b: Long,   // Buyer order ID
    val a: Long,   // Seller order ID
    @SerializedName("T") val tradeTime: Long,   // Trade time
    val m: Boolean // Is buyer the market maker?
)

// Binance WebSocket Ticker Stream (24hr ticker)
// Stream: btcusdt@ticker
data class BinanceTickerStream(
    val e: String,  // Event type
    @SerializedName("E") val eventTime: Long,    // Event time
    val s: String,  // Symbol
    val c: String,  // Close price (last price)
    val o: String,  // Open price
    val h: String,  // High price
    val l: String,  // Low price
    val v: String,  // Total traded base asset volume
    val q: String,  // Total traded quote asset volume
    val p: String,  // Price change
    @SerializedName("P") val priceChangePercent: String,  // Price change percent
    val w: String,  // Weighted average price
    val x: String,  // First trade price
    @SerializedName("Q") val lastQuantity: String   // Last quantity
)

// Binance WebSocket Book Ticker Stream
// Stream: btcusdt@bookTicker
data class BinanceBookTickerStream(
    val e: String,  // Event type ("bookTicker")
    @SerializedName("E") val eventTime: Long,    // Event time
    @SerializedName("T") val transactionTime: Long, // Transaction time
    val s: String,  // Symbol
    val u: Long,    // Order book update ID
    val b: String,  // Best bid price
    @SerializedName("B") val bestBidQty: String, // Best bid quantity
    val a: String,  // Best ask price
    @SerializedName("A") val bestAskQty: String  // Best ask quantity
)

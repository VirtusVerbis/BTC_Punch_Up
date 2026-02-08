package com.example.myapp.data

// Coinbase WebSocket Message Types
sealed class CoinbaseWebSocketMessage {
    abstract val type: String
}

// Subscribe message
data class CoinbaseSubscribeMessage(
    override val type: String = "subscribe",
    val product_ids: List<String> = listOf("BTC-USD"),
    val channels: List<String>
) : CoinbaseWebSocketMessage()

// Ticker message
data class CoinbaseTickerMessage(
    override val type: String,
    val sequence: Long,
    val product_id: String,
    val price: String,
    val open_24h: String?,
    val volume_24h: String?,
    val low_24h: String?,
    val high_24h: String?,
    val volume_30d: String?,
    val best_bid: String?,
    val best_ask: String?,
    val side: String?,
    val time: String?,
    val trade_id: Long?,
    val last_size: String?
) : CoinbaseWebSocketMessage()

// Level2 snapshot message
data class CoinbaseLevel2SnapshotMessage(
    override val type: String,
    val product_id: String,
    val bids: List<List<String>>, // [price, size]
    val asks: List<List<String>>  // [price, size]
) : CoinbaseWebSocketMessage()

// Level2 update message
data class CoinbaseLevel2UpdateMessage(
    override val type: String,
    val product_id: String,
    val changes: List<List<String>> // [side, price, size] where side is "buy" or "sell"
) : CoinbaseWebSocketMessage()

// Matches (trades) message
data class CoinbaseMatchMessage(
    override val type: String,
    val trade_id: Long,
    val sequence: Long,
    val maker_order_id: String,
    val taker_order_id: String,
    val time: String,
    val product_id: String,
    val size: String,
    val price: String,
    val side: String // "buy" or "sell"
) : CoinbaseWebSocketMessage()

// Heartbeat message
data class CoinbaseHeartbeatMessage(
    override val type: String,
    val sequence: Long,
    val last_trade_id: Long,
    val product_id: String,
    val time: String
) : CoinbaseWebSocketMessage()

// Error message
data class CoinbaseErrorMessage(
    override val type: String,
    val message: String
) : CoinbaseWebSocketMessage()

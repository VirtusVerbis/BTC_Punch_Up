package com.example.myapp.data

import android.util.Log
import com.example.myapp.BG2_CANDLE_EMIT_THROTTLE_MS
import com.example.myapp.BG2_MAX_CANDLES
import com.example.myapp.EXCHANGE_EMIT_THROTTLE_MS
import com.example.myapp.ENABLE_EXCHANGE_LOGS
import com.example.myapp.SPREAD_DEFENSE_MIN_PERCENT
import com.example.myapp.SPREAD_DEFENSE_MULTIPLIER
import com.example.myapp.SPREAD_MEDIAN_WINDOW_SECONDS
import com.google.gson.Gson
import com.google.gson.JsonArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class ExchangeData(
    val price: Double? = null,
    val buyVolume: Double? = null,
    val sellVolume: Double? = null,
    val isConnected: Boolean = false,
    val bestBid: Double? = null,
    val bestAsk: Double? = null,
    val spreadPercent: Double? = null,
    val medianSpreadPercent: Double? = null,
    val isDefenseMode: Boolean = false
)

class WebSocketRepository {
    private val binanceService = BinanceWebSocketService()
    private val coinbaseService = CoinbaseWebSocketService()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // State flows for exchange data
    private val _binanceData = MutableStateFlow(ExchangeData())
    val binanceData: StateFlow<ExchangeData> = _binanceData.asStateFlow()
    
    private val _coinbaseData = MutableStateFlow(ExchangeData())
    val coinbaseData: StateFlow<ExchangeData> = _coinbaseData.asStateFlow()

    // 1-min candles for BG2 chart (in-memory, capped, not persisted)
    private val _candleData = MutableStateFlow<List<Candle>>(emptyList())
    val candleData: StateFlow<List<Candle>> = _candleData.asStateFlow()
    private val candleBuckets = mutableMapOf<Long, Candle>()
    private val candleLock = Any()
    @Volatile private var candleBackfillDone = false

    // Track volumes for aggregation
    @Volatile private var binanceBuyVolumeAccumulator = 0.0
    @Volatile private var binanceSellVolumeAccumulator = 0.0
    @Volatile private var coinbaseBuyVolumeAccumulator = 0.0
    @Volatile private var coinbaseSellVolumeAccumulator = 0.0

    // Latest non-volume fields (we emit snapshots on a fixed interval to throttle UI updates)
    @Volatile private var latestBinancePrice: Double? = null
    @Volatile private var latestBinanceIsConnected: Boolean = false
    @Volatile private var latestBinanceBestBid: Double? = null
    @Volatile private var latestBinanceBestAsk: Double? = null
    @Volatile private var latestBinanceSpreadPercent: Double? = null
    @Volatile private var latestBinanceMedianSpreadPercent: Double? = null
    @Volatile private var latestBinanceIsDefenseMode: Boolean = false

    @Volatile private var latestCoinbasePrice: Double? = null
    @Volatile private var latestCoinbaseIsConnected: Boolean = false
    @Volatile private var latestCoinbaseBestBid: Double? = null
    @Volatile private var latestCoinbaseBestAsk: Double? = null
    @Volatile private var latestCoinbaseSpreadPercent: Double? = null
    @Volatile private var latestCoinbaseMedianSpreadPercent: Double? = null
    @Volatile private var latestCoinbaseIsDefenseMode: Boolean = false

    // Track spread history for median calculation (timestampMs to spreadPercent)
    private val binanceSpreadHistory = mutableListOf<Pair<Long, Double>>()
    private val coinbaseSpreadHistory = mutableListOf<Pair<Long, Double>>()

    // Coinbase Level2 order book (price -> size) for best bid/ask from snapshot + l2update
    private val coinbaseBidBook = mutableMapOf<Double, Double>()
    private val coinbaseAskBook = mutableMapOf<Double, Double>()
    
    // Volume reset interval (reset every 5 seconds to match polling behavior)
    private val volumeResetInterval = 5000L
    
    init {
        startCollecting()
    }
    
    private fun startCollecting() {
        // Emit snapshots at a fixed interval to throttle UI-driven state changes.
        // We still collect/accumulate every message, but only publish ExchangeData ~1/EXCHANGE_EMIT_THROTTLE_MS times per second.
        scope.launch {
            while (true) {
                delay(EXCHANGE_EMIT_THROTTLE_MS)
                _binanceData.value = ExchangeData(
                    price = latestBinancePrice,
                    buyVolume = binanceBuyVolumeAccumulator,
                    sellVolume = binanceSellVolumeAccumulator,
                    isConnected = latestBinanceIsConnected,
                    bestBid = latestBinanceBestBid,
                    bestAsk = latestBinanceBestAsk,
                    spreadPercent = latestBinanceSpreadPercent,
                    medianSpreadPercent = latestBinanceMedianSpreadPercent,
                    isDefenseMode = latestBinanceIsDefenseMode
                )
            }
        }

        scope.launch {
            while (true) {
                delay(EXCHANGE_EMIT_THROTTLE_MS)
                _coinbaseData.value = ExchangeData(
                    price = latestCoinbasePrice,
                    buyVolume = coinbaseBuyVolumeAccumulator,
                    sellVolume = coinbaseSellVolumeAccumulator,
                    isConnected = latestCoinbaseIsConnected,
                    bestBid = latestCoinbaseBestBid,
                    bestAsk = latestCoinbaseBestAsk,
                    spreadPercent = latestCoinbaseSpreadPercent,
                    medianSpreadPercent = latestCoinbaseMedianSpreadPercent,
                    isDefenseMode = latestCoinbaseIsDefenseMode
                )
            }
        }

        // Collect Binance data
        scope.launch {
            binanceService.priceFlow
                .catch { e ->
                    if (ENABLE_EXCHANGE_LOGS) {
                        Log.e("WebSocketRepository", "Binance price flow error: ${e.message}", e)
                    }
                }
                .collect { price ->
                    latestBinancePrice = price
                    latestBinanceIsConnected = true
                }
        }
        
        scope.launch {
            binanceService.tradeFlow
                .catch { e ->
                    if (ENABLE_EXCHANGE_LOGS) {
                        Log.e("WebSocketRepository", "Binance trade flow error: ${e.message}", e)
                    }
                }
                .collect { trade ->
                    val qty = trade.q.toDoubleOrNull() ?: 0.0
                    if (trade.m) {
                        binanceSellVolumeAccumulator += qty
                    } else {
                        binanceBuyVolumeAccumulator += qty
                    }
                    latestBinanceIsConnected = true
                    // Aggregate into 1-min candle (bucket by trade time)
                    val price = trade.p.toDoubleOrNull() ?: return@collect
                    val bucketOpenTime = (trade.tradeTime / 60_000) * 60_000
                    synchronized(candleLock) {
                        val existing = candleBuckets[bucketOpenTime]
                        if (existing == null) {
                            candleBuckets[bucketOpenTime] = Candle(
                                openTime = bucketOpenTime,
                                open = price,
                                high = price,
                                low = price,
                                close = price
                            )
                        } else {
                            candleBuckets[bucketOpenTime] = existing.copy(
                                high = maxOf(existing.high, price),
                                low = minOf(existing.low, price),
                                close = price
                            )
                        }
                    }
                }
        }

        // Throttled emit of candle list to UI
        scope.launch {
            while (true) {
                delay(BG2_CANDLE_EMIT_THROTTLE_MS)
                val list = synchronized(candleLock) {
                    val sorted = candleBuckets.values.sortedBy { it.openTime }
                    if (sorted.size > BG2_MAX_CANDLES) {
                        sorted.dropLast(BG2_MAX_CANDLES).forEach { candleBuckets.remove(it.openTime) }
                    }
                    candleBuckets.values.sortedBy { it.openTime }
                }
                if (list.isNotEmpty()) {
                    _candleData.value = list
                }
            }
        }

        // One-time REST klines backfill for first-time experience.
        // Only skip if we already have enough candles (e.g. from a previous run); do not skip
        // just because one trade created a single bucket — that would leave the initial chart with 1 candle.
        scope.launch {
            delay(2000)
            if (candleBackfillDone) return@launch
            synchronized(candleLock) {
                if (candleBuckets.size >= BG2_MAX_CANDLES) {
                    candleBackfillDone = true
                    return@launch
                }
            }
            try {
                val api = Retrofit.Builder()
                    .baseUrl("https://api.binance.com/")
                    .addConverterFactory(GsonConverterFactory.create(Gson()))
                    .build()
                    .create(CryptoApiService::class.java)
                val body = withContext(Dispatchers.IO) {
                    api.getBinanceKlines(interval = "1m", limit = BG2_MAX_CANDLES)
                }
                val json = body.string()
                val gson = Gson()
                val arr = gson.fromJson(json, JsonArray::class.java) ?: return@launch
                val backfill = mutableListOf<Candle>()
                for (i in 0 until arr.size()) {
                    val row = arr.get(i).asJsonArray
                    if (row.size() < 5) continue
                    val openTime = row.get(0).asLong
                    val open = row.get(1).asDouble
                    val high = row.get(2).asDouble
                    val low = row.get(3).asDouble
                    val close = row.get(4).asDouble
                    backfill.add(Candle(openTime, open, high, low, close))
                }
                synchronized(candleLock) {
                    backfill.forEach { c -> candleBuckets[c.openTime] = c }
                    candleBackfillDone = true
                    val emitted = candleBuckets.values.sortedBy { it.openTime }.takeLast(BG2_MAX_CANDLES)
                    _candleData.value = emitted
                }
            } catch (e: Exception) {
                if (ENABLE_EXCHANGE_LOGS) {
                    Log.e("WebSocketRepository", "Klines backfill failed: ${e.message}", e)
                }
                candleBackfillDone = true
            }
        }

        // Collect Binance book ticker data (best bid/ask, spread)
        scope.launch {
            binanceService.bookTickerFlow
                .catch { e ->
                    if (ENABLE_EXCHANGE_LOGS) {
                        Log.e("WebSocketRepository", "Binance bookTicker flow error: ${e.message}", e)
                    }
                }
                .collect { bookTicker ->
                    val bid = bookTicker.b.toDoubleOrNull()
                    val ask = bookTicker.a.toDoubleOrNull()
                    if (bid != null && ask != null && bid > 0.0 && ask > 0.0 && ask >= bid) {
                        val mid = (bid + ask) / 2.0
                        val spread = ask - bid
                        val spreadPercent = (spread / mid) * 100.0

                        val (medianSpread, isDefense) = updateSpreadHistoryAndDefense(
                            history = binanceSpreadHistory,
                            newSpreadPercent = spreadPercent
                        )

                        latestBinanceBestBid = bid
                        latestBinanceBestAsk = ask
                        latestBinanceSpreadPercent = spreadPercent
                        latestBinanceMedianSpreadPercent = medianSpread
                        latestBinanceIsDefenseMode = isDefense
                        latestBinanceIsConnected = true
                    }
                }
        }
        
        // Collect Coinbase data
        scope.launch {
            coinbaseService.priceFlow
                .catch { e ->
                    if (ENABLE_EXCHANGE_LOGS) {
                        Log.e("WebSocketRepository", "Coinbase price flow error: ${e.message}", e)
                    }
                }
                .collect { price ->
                    latestCoinbasePrice = price
                    latestCoinbaseIsConnected = true
                }
        }

        // Coinbase best bid/ask from Level2 snapshot + l2update (consistent, high-frequency stream)
        scope.launch {
            coinbaseService.level2Flow
                .catch { e ->
                    if (ENABLE_EXCHANGE_LOGS) {
                        Log.e("WebSocketRepository", "Coinbase level2 flow error: ${e.message}", e)
                    }
                }
                .collect { snapshot ->
                    // Rebuild order book from snapshot
                    coinbaseBidBook.clear()
                    coinbaseAskBook.clear()
                    snapshot.bids.forEach { level ->
                        val price = level.getOrNull(0)?.toDoubleOrNull()
                        val size = level.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                        if (price != null && size > 0.0) coinbaseBidBook[price] = size
                    }
                    snapshot.asks.forEach { level ->
                        val price = level.getOrNull(0)?.toDoubleOrNull()
                        val size = level.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                        if (price != null && size > 0.0) coinbaseAskBook[price] = size
                    }
                    updateCoinbaseSpreadFromBook()
                    // Also update volumes from snapshot
                    var buyVolume = 0.0
                    var sellVolume = 0.0
                    snapshot.bids.take(50).forEach { bid ->
                        val size = bid.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                        buyVolume += size
                    }
                    snapshot.asks.take(50).forEach { ask ->
                        val size = ask.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                        sellVolume += size
                    }
                    coinbaseBuyVolumeAccumulator = buyVolume
                    coinbaseSellVolumeAccumulator = sellVolume
                    latestCoinbaseIsConnected = true
                }
        }

        scope.launch {
            coinbaseService.level2UpdateFlow
                .catch { e ->
                    if (ENABLE_EXCHANGE_LOGS) {
                        Log.e("WebSocketRepository", "Coinbase l2update flow error: ${e.message}", e)
                    }
                }
                .collect { update ->
                    update.changes.forEach { change ->
                        val side = change.getOrNull(0)
                        val price = change.getOrNull(1)?.toDoubleOrNull()
                        val size = change.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                        if (price == null) return@forEach
                        when (side) {
                            "buy" -> if (size <= 0.0) coinbaseBidBook.remove(price) else coinbaseBidBook[price] = size
                            "sell" -> if (size <= 0.0) coinbaseAskBook.remove(price) else coinbaseAskBook[price] = size
                        }
                    }
                    updateCoinbaseSpreadFromBook()
                    latestCoinbaseIsConnected = true
                }
        }
        
        scope.launch {
            coinbaseService.matchFlow
                .catch { e ->
                    if (ENABLE_EXCHANGE_LOGS) {
                        Log.e("WebSocketRepository", "Coinbase match flow error: ${e.message}", e)
                    }
                }
                .collect { match ->
                    val size = match.size.toDoubleOrNull() ?: 0.0
                    if (match.side == "buy") {
                        coinbaseBuyVolumeAccumulator += size
                    } else {
                        coinbaseSellVolumeAccumulator += size
                    }
                    latestCoinbaseIsConnected = true
                }
        }
        
        // Reset volume accumulators periodically
        scope.launch {
            while (true) {
                delay(volumeResetInterval)
                binanceBuyVolumeAccumulator = 0.0
                binanceSellVolumeAccumulator = 0.0
                coinbaseBuyVolumeAccumulator = 0.0
                coinbaseSellVolumeAccumulator = 0.0
                
                if (ENABLE_EXCHANGE_LOGS) {
                    Log.d("WebSocketRepository", "Volume accumulators reset")
                }
            }
        }
        
        // Reconnection logic
        scope.launch {
            while (true) {
                delay(10000) // Check every 10 seconds
                
                if (!binanceService.isConnected()) {
                    if (ENABLE_EXCHANGE_LOGS) {
                        Log.d("WebSocketRepository", "Reconnecting Binance WebSocket...")
                    }
                    _binanceData.value = _binanceData.value.copy(isConnected = false)
                    binanceService.connect()
                }
                
                if (!coinbaseService.isConnected()) {
                    if (ENABLE_EXCHANGE_LOGS) {
                        Log.d("WebSocketRepository", "Reconnecting Coinbase WebSocket...")
                    }
                    _coinbaseData.value = _coinbaseData.value.copy(isConnected = false)
                    coinbaseService.connect()
                }
            }
        }
    }

    /**
     * Update spread history for an exchange and determine defense mode.
     *
     * @param history Mutable list of (timestampMs, spreadPercent) for the exchange.
     * @param newSpreadPercent Latest spread percentage value.
     * @return Pair of (medianSpreadPercent, isDefenseMode).
     */
    private fun updateSpreadHistoryAndDefense(
        history: MutableList<Pair<Long, Double>>,
        newSpreadPercent: Double
    ): Pair<Double, Boolean> {
        val now = System.currentTimeMillis()
        history.add(now to newSpreadPercent)

        // Remove entries older than the configured window
        val cutoff = now - SPREAD_MEDIAN_WINDOW_SECONDS * 1000
        history.removeAll { it.first < cutoff }

        // Compute median spread over the window
        val median = if (history.isNotEmpty()) {
            val sorted = history.map { it.second }.sorted()
            val n = sorted.size
            if (n % 2 == 1) {
                sorted[n / 2]
            } else {
                (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
            }
        } else {
            newSpreadPercent
        }

        // Determine defense mode based on median and current spread
        val isDefense =
            newSpreadPercent > median * SPREAD_DEFENSE_MULTIPLIER &&
            newSpreadPercent > SPREAD_DEFENSE_MIN_PERCENT

        if (ENABLE_EXCHANGE_LOGS) {
            Log.d(
                "WebSocketRepository",
                "Spread update - current=${newSpreadPercent}%, median=${median}%, defense=$isDefense"
            )
        }

        return median to isDefense
    }

    /**
     * Derive best bid/ask from Coinbase Level2 order book, compute spread,
     * update spread history and defense mode, and emit updated Coinbase data.
     */
    private fun updateCoinbaseSpreadFromBook() {
        val bestBid = coinbaseBidBook.keys.maxOrNull()
        val bestAsk = coinbaseAskBook.keys.minOrNull()
        if (bestBid == null || bestAsk == null || bestBid <= 0.0 || bestAsk <= 0.0 || bestAsk < bestBid) {
            return
        }
        val mid = (bestBid + bestAsk) / 2.0
        val spread = bestAsk - bestBid
        val spreadPercent = (spread / mid) * 100.0

        val (medianSpread, isDefense) = updateSpreadHistoryAndDefense(
            history = coinbaseSpreadHistory,
            newSpreadPercent = spreadPercent
        )

        latestCoinbaseBestBid = bestBid
        latestCoinbaseBestAsk = bestAsk
        latestCoinbaseSpreadPercent = spreadPercent
        latestCoinbaseMedianSpreadPercent = medianSpread
        latestCoinbaseIsDefenseMode = isDefense
        latestCoinbaseIsConnected = true
    }
    
    fun connect() {
        if (ENABLE_EXCHANGE_LOGS) {
            Log.d("WebSocketRepository", "Connecting WebSockets...")
        }
        binanceService.connect()
        coinbaseService.connect()
    }
    
    fun disconnect() {
        if (ENABLE_EXCHANGE_LOGS) {
            Log.d("WebSocketRepository", "Disconnecting WebSockets...")
        }
        binanceService.disconnect()
        coinbaseService.disconnect()
    }

    /**
     * Fetch 1m klines from startTimeMs up to the last full minute and merge into chart data.
     * Call on app resume to fill the chart gap for the time the app was in background.
     */
    fun backfillCandlesSince(startTimeMs: Long) {
        scope.launch {
            try {
                val bucketMs = 60_000L
                val startBucket = (startTimeMs / bucketMs) * bucketMs
                val now = System.currentTimeMillis()
                val endBucket = (now / bucketMs) * bucketMs
                val limit = ((endBucket - startBucket) / bucketMs).toInt().coerceIn(1, BG2_MAX_CANDLES)
                if (limit <= 0) return@launch
                val api = Retrofit.Builder()
                    .baseUrl("https://api.binance.com/")
                    .addConverterFactory(GsonConverterFactory.create(Gson()))
                    .build()
                    .create(CryptoApiService::class.java)
                val body = withContext(Dispatchers.IO) {
                    api.getBinanceKlines(interval = "1m", limit = limit, startTime = startBucket, endTime = endBucket + bucketMs)
                }
                val json = body.string()
                val gson = Gson()
                val arr = gson.fromJson(json, JsonArray::class.java) ?: return@launch
                val backfill = mutableListOf<Candle>()
                for (i in 0 until arr.size()) {
                    val row = arr.get(i).asJsonArray
                    if (row.size() < 5) continue
                    val openTime = row.get(0).asLong
                    val open = row.get(1).asDouble
                    val high = row.get(2).asDouble
                    val low = row.get(3).asDouble
                    val close = row.get(4).asDouble
                    backfill.add(Candle(openTime, open, high, low, close))
                }
                synchronized(candleLock) {
                    backfill.forEach { c -> candleBuckets[c.openTime] = c }
                    val sorted = candleBuckets.values.sortedBy { it.openTime }
                    val toDrop = sorted.size - BG2_MAX_CANDLES
                    if (toDrop > 0) {
                        sorted.take(toDrop).forEach { candleBuckets.remove(it.openTime) }
                    }
                    _candleData.value = candleBuckets.values.sortedBy { it.openTime }.takeLast(BG2_MAX_CANDLES)
                }
            } catch (e: Exception) {
                if (ENABLE_EXCHANGE_LOGS) {
                    Log.e("WebSocketRepository", "Resume candle backfill failed: ${e.message}", e)
                }
            }
        }
    }

    fun cleanup() {
        disconnect()
        scope.cancel()
    }
}

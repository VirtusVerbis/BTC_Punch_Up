package com.vv.btcpunchup.data

import android.util.Log
import com.vv.btcpunchup.ENABLE_EXCHANGE_LOGS
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class BinanceWebSocketService {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS) // Binance requires ping every 20 seconds
        .build()
    
    private var webSocket: WebSocket? = null
    private var isConnected = false
    
    // Channels for data streams
    private val priceChannel = Channel<Double>(Channel.UNLIMITED)
    private val tradeChannel = Channel<BinanceTradeStream>(Channel.UNLIMITED)
    private val bookTickerChannel = Channel<BinanceBookTickerStream>(Channel.UNLIMITED)
    
    // Flows for consumers
    val priceFlow: Flow<Double> = priceChannel.receiveAsFlow()
    val tradeFlow: Flow<BinanceTradeStream> = tradeChannel.receiveAsFlow()
    val bookTickerFlow: Flow<BinanceBookTickerStream> = bookTickerChannel.receiveAsFlow()
    
    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isConnected = true
            if (ENABLE_EXCHANGE_LOGS) {
                Log.d("BinanceWebSocket", "WebSocket connected (trade + ticker + bookTicker)")
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val jsonObject = gson.fromJson(text, JsonObject::class.java)
                val stream = jsonObject.get("stream")?.asString
                val data = jsonObject.get("data")?.asJsonObject ?: return
                val e = data.get("e")?.asString ?: return
                when (e) {
                    "trade" -> {
                        val tradeStream = gson.fromJson(data, BinanceTradeStream::class.java)
                        val price = tradeStream.p.toDoubleOrNull()
                        if (price != null) priceChannel.trySend(price)
                        tradeChannel.trySend(tradeStream)
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.d("BinanceWebSocket", "Trade: price=${tradeStream.p}, qty=${tradeStream.q}, isBuyerMaker=${tradeStream.m}")
                        }
                    }
                    "24hrTicker" -> {
                        val tickerStream = gson.fromJson(data, BinanceTickerStream::class.java)
                        val price = tickerStream.c.toDoubleOrNull()
                        if (price != null) priceChannel.trySend(price)
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.d("BinanceWebSocket", "Ticker: price=${tickerStream.c}")
                        }
                    }
                    "bookTicker" -> {
                        val bookTickerStream = gson.fromJson(data, BinanceBookTickerStream::class.java)
                        bookTickerChannel.trySend(bookTickerStream)
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.d("BinanceWebSocket", "BookTicker: bid=${bookTickerStream.b}, ask=${bookTickerStream.a}")
                        }
                    }
                    else -> if (ENABLE_EXCHANGE_LOGS && stream != null) {
                        Log.d("BinanceWebSocket", "Main stream unknown event: stream=$stream e=$e")
                    }
                }
            } catch (e: Exception) {
                if (ENABLE_EXCHANGE_LOGS) {
                    Log.e("BinanceWebSocket", "Main stream parse error: ${e.message}", e)
                }
            }
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            isConnected = false
            if (ENABLE_EXCHANGE_LOGS) Log.e("BinanceWebSocket", "WebSocket failure: ${t.message}", t)
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            isConnected = false
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isConnected = false
        }
    }
    
    fun connect() {
        if (webSocket != null && isConnected) {
            if (ENABLE_EXCHANGE_LOGS) Log.d("BinanceWebSocket", "Already connected")
            return
        }
        
        // Single combined stream: trade, ticker, and bookTicker (best bid/ask for Defense)
        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/stream?streams=btcusdt@trade/btcusdt@ticker/btcusdt@bookTicker")
            .build()
        webSocket = client.newWebSocket(request, listener)
        
        if (ENABLE_EXCHANGE_LOGS) {
            Log.d("BinanceWebSocket", "Connecting to Binance WebSocket...")
        }
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
        if (ENABLE_EXCHANGE_LOGS) {
            Log.d("BinanceWebSocket", "Disconnected")
        }
    }
    
    fun isConnected(): Boolean = isConnected
}

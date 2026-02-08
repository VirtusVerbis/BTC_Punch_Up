package com.example.myapp.data

import android.util.Log
import com.example.myapp.ENABLE_EXCHANGE_LOGS
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

class CoinbaseWebSocketService {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .build()
    
    private var webSocket: WebSocket? = null
    private var isConnected = false
    
    // Channels for data streams
    private val priceChannel = Channel<Double>(Channel.UNLIMITED)
    private val tickerChannel = Channel<CoinbaseTickerMessage>(Channel.UNLIMITED)
    private val level2Channel = Channel<CoinbaseLevel2SnapshotMessage>(Channel.UNLIMITED)
    private val level2UpdateChannel = Channel<CoinbaseLevel2UpdateMessage>(Channel.UNLIMITED)
    private val matchChannel = Channel<CoinbaseMatchMessage>(Channel.UNLIMITED)
    
    // Flows for consumers
    val priceFlow: Flow<Double> = priceChannel.receiveAsFlow()
    val tickerFlow: Flow<CoinbaseTickerMessage> = tickerChannel.receiveAsFlow()
    val level2Flow: Flow<CoinbaseLevel2SnapshotMessage> = level2Channel.receiveAsFlow()
    val level2UpdateFlow: Flow<CoinbaseLevel2UpdateMessage> = level2UpdateChannel.receiveAsFlow()
    val matchFlow: Flow<CoinbaseMatchMessage> = matchChannel.receiveAsFlow()
    
    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isConnected = true
            if (ENABLE_EXCHANGE_LOGS) {
                Log.d("CoinbaseWebSocket", "WebSocket connected")
            }
            
            // Subscribe to channels
            val subscribeMessage = CoinbaseSubscribeMessage(
                channels = listOf("ticker", "level2", "matches", "heartbeats")
            )
            val json = gson.toJson(subscribeMessage)
            webSocket.send(json)
            
            if (ENABLE_EXCHANGE_LOGS) {
                Log.d("CoinbaseWebSocket", "Subscribed to channels: ticker, level2, matches, heartbeats")
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val jsonObject = gson.fromJson(text, JsonObject::class.java)
                val type = jsonObject.get("type")?.asString ?: return
                
                when (type) {
                    "ticker" -> {
                        val ticker = gson.fromJson(text, CoinbaseTickerMessage::class.java)
                        tickerChannel.trySend(ticker)
                        // Extract price
                        val price = ticker.price.toDoubleOrNull()
                        if (price != null) {
                            priceChannel.trySend(price)
                        }
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.d("CoinbaseWebSocket", "Ticker: price=${ticker.price}")
                        }
                    }
                    "snapshot" -> {
                        val snapshot = gson.fromJson(text, CoinbaseLevel2SnapshotMessage::class.java)
                        level2Channel.trySend(snapshot)
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.d("CoinbaseWebSocket", "Level2 Snapshot: bids=${snapshot.bids.size}, asks=${snapshot.asks.size}")
                        }
                    }
                    "l2update" -> {
                        val update = gson.fromJson(text, CoinbaseLevel2UpdateMessage::class.java)
                        level2UpdateChannel.trySend(update)
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.d("CoinbaseWebSocket", "Level2 Update: changes=${update.changes.size}")
                        }
                    }
                    "match" -> {
                        val match = gson.fromJson(text, CoinbaseMatchMessage::class.java)
                        matchChannel.trySend(match)
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.d("CoinbaseWebSocket", "Match: price=${match.price}, size=${match.size}, side=${match.side}")
                        }
                    }
                    "heartbeat" -> {
                        // Keep connection alive
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.d("CoinbaseWebSocket", "Heartbeat received")
                        }
                    }
                    "error" -> {
                        val error = gson.fromJson(text, CoinbaseErrorMessage::class.java)
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.e("CoinbaseWebSocket", "Error: ${error.message}")
                        }
                    }
                    "subscriptions" -> {
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.d("CoinbaseWebSocket", "Subscription confirmed")
                        }
                    }
                }
            } catch (e: Exception) {
                if (ENABLE_EXCHANGE_LOGS) {
                    Log.e("CoinbaseWebSocket", "Error parsing message: ${e.message}", e)
                }
            }
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            isConnected = false
            if (ENABLE_EXCHANGE_LOGS) {
                Log.e("CoinbaseWebSocket", "WebSocket failure: ${t.message}", t)
            }
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            isConnected = false
            if (ENABLE_EXCHANGE_LOGS) {
                Log.d("CoinbaseWebSocket", "WebSocket closing: $code - $reason")
            }
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isConnected = false
            if (ENABLE_EXCHANGE_LOGS) {
                Log.d("CoinbaseWebSocket", "WebSocket closed: $code - $reason")
            }
        }
    }
    
    fun connect() {
        if (webSocket != null && isConnected) {
            if (ENABLE_EXCHANGE_LOGS) {
                Log.d("CoinbaseWebSocket", "Already connected")
            }
            return
        }
        
        val request = Request.Builder()
            .url("wss://ws-feed.exchange.coinbase.com")
            .build()
        
        webSocket = client.newWebSocket(request, listener)
        
        if (ENABLE_EXCHANGE_LOGS) {
            Log.d("CoinbaseWebSocket", "Connecting to Coinbase WebSocket...")
        }
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
        if (ENABLE_EXCHANGE_LOGS) {
            Log.d("CoinbaseWebSocket", "Disconnected")
        }
    }
    
    fun isConnected(): Boolean = isConnected
}

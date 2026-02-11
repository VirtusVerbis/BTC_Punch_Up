package com.vv.btcpunchup.data

data class CoinbaseOrderBookResponse(
    val sequence: Long,
    val bids: List<List<String>>, // [price, size, num-orders]
    val asks: List<List<String>>  // [price, size, num-orders]
)

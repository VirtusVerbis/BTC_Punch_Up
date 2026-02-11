package com.vv.btcpunchup.data

data class CoinbasePriceResponse(
    val data: CoinbasePriceData
)

data class CoinbasePriceData(
    val base: String,
    val currency: String,
    val amount: String
)

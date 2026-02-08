package com.example.myapp.data

/**
 * One 1-minute OHLC candle for the BG2 chart.
 * openTime is the start of the minute (ms epoch).
 */
data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

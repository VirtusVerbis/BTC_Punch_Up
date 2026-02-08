package com.example.myapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.myapp.data.Candle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CHART_BG = Color.Black
private val CANDLE_GREEN = Color(0xFF00C853)
private val CANDLE_RED = Color(0xFFD50000)
private val LABEL_COLOR = Color.White.copy(alpha = 0.8f)
private val PADDING_PX = 4f
private val MIN_CANDLE_WIDTH = 2f
private val WICK_WIDTH = 1f
private val Y_AXIS_WIDTH_DP = 40
private val X_AXIS_HEIGHT_DP = 20
private val NUM_Y_TICKS = 5
private val NUM_X_TICKS = 5

/** Compact Y-axis label: e.g. 72232 -> "72.2K", 98000 -> "98K", 1.2e6 -> "1.2M". */
private fun formatPriceShort(price: Double): String {
    return when {
        price >= 1_000_000 -> {
            val v = price / 1e6
            if (v == v.toLong().toDouble()) "%.0fM".format(Locale.US, v) else "%.1fM".format(Locale.US, v)
        }
        price >= 1_000 -> {
            val v = price / 1e3
            if (v == v.toLong().toDouble()) "%.0fK".format(Locale.US, v) else "%.1fK".format(Locale.US, v)
        }
        else -> "%.0f".format(Locale.US, price)
    }
}

/**
 * Draws a 1-min Bitcoin candlestick chart in the given space.
 * X = time (1-min intervals), Y = USD price. Black background, green/red candles.
 * When showAxisLabels is true, shows Y (price) and X (time) labels.
 */
@Composable
fun BtcCandleChart(
    candles: List<Candle>,
    showAxisLabels: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!showAxisLabels) {
        ChartCanvas(candles = candles, modifier = modifier.fillMaxSize().background(CHART_BG))
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CHART_BG)
    ) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            ChartCanvas(candles = candles, modifier = Modifier.fillMaxHeight().weight(1f))
            if (candles.isNotEmpty()) {
                val priceMin = candles.minOf { it.low }
                val priceMax = candles.maxOf { it.high }
                val pad = (priceMax - priceMin).coerceAtLeast(1.0) * 0.02
                val yMin = priceMin - pad
                val yMax = priceMax + pad
                val step = (yMax - yMin) / (NUM_Y_TICKS - 1).coerceAtLeast(1)
                val yTicks = (0 until NUM_Y_TICKS).map { yMax - it * step }
                Column(
                    modifier = Modifier
                        .width(Y_AXIS_WIDTH_DP.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    yTicks.forEach { price ->
                        Text(
                            text = "$${formatPriceShort(price)}",
                            color = LABEL_COLOR,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
        if (candles.size >= NUM_X_TICKS) {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val indices = (0 until NUM_X_TICKS).map { i ->
                (i * (candles.size - 1) / (NUM_X_TICKS - 1).coerceAtLeast(1))
            }.distinct()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(X_AXIS_HEIGHT_DP.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                indices.forEach { i ->
                    val openTime = candles[i].openTime
                    Text(
                        text = timeFormat.format(Date(openTime)),
                        color = LABEL_COLOR,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCanvas(
    candles: List<Candle>,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (candles.isEmpty() || width <= 0 || height <= 0) return@Canvas

        val priceMin = candles.minOf { it.low }
        val priceMax = candles.maxOf { it.high }
        val priceRange = (priceMax - priceMin).coerceAtLeast(1.0)
        val pad = priceRange * 0.02
        val yMin = priceMin - pad
        val yMax = priceMax + pad
        val yRange = (yMax - yMin).coerceAtLeast(1.0)

        val chartLeft = PADDING_PX
        val chartRight = width - PADDING_PX
        val chartTop = PADDING_PX
        val chartBottom = height - PADDING_PX
        val chartW = (chartRight - chartLeft).coerceAtLeast(1f)
        val chartH = (chartBottom - chartTop).coerceAtLeast(1f)

        val n = candles.size
        val candleW = (chartW / n).coerceAtLeast(MIN_CANDLE_WIDTH)
        val gap = if (n > 1) (chartW - candleW * n) / (n - 1) else 0f

        candles.forEachIndexed { index, c ->
            val xCenter = chartLeft + (index + 0.5f) * (candleW + gap)
            val openY = chartBottom - ((c.open - yMin) / yRange * chartH).toFloat()
            val closeY = chartBottom - ((c.close - yMin) / yRange * chartH).toFloat()
            val highY = chartBottom - ((c.high - yMin) / yRange * chartH).toFloat()
            val lowY = chartBottom - ((c.low - yMin) / yRange * chartH).toFloat()

            val bodyTop = minOf(openY, closeY)
            val bodyBottom = maxOf(openY, closeY)
            val bodyHeight = (bodyBottom - bodyTop).coerceAtLeast(1f)
            val isGreen = c.close >= c.open

            val color = if (isGreen) CANDLE_GREEN else CANDLE_RED
            drawLine(
                color = color,
                start = Offset(xCenter, highY),
                end = Offset(xCenter, lowY),
                strokeWidth = WICK_WIDTH
            )
            drawRect(
                color = color,
                topLeft = Offset(xCenter - candleW / 2, bodyTop),
                size = Size(candleW, bodyHeight)
            )
        }
    }
}

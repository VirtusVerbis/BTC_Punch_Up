# BTC Punch Up

A boxing-themed Android app that visualizes real-time Bitcoin markets: Satoshi (hero) vs Lizard (villain), driven by live price and order-book volume from Binance and Coinbase.

## Features

- **Real-time dual-exchange data** – WebSocket connections to Binance and Coinbase for live BTC price and buy/sell volume
- **Boxing mechanics** – Satoshi vs Lizard; offense/defense and punch types derived from volume
- **Damage and KO** – Damage bars, landed hits add points, KO sequence (Fall → Knocked Down → Rise) when bar reaches 100
- **Layers** – Splash (tap or 3s dismiss), audience (ring-synced), buy_btc_sign signs, optional candle chart, ring, boxers, cat
- **Overlay** – Price per exchange, block height (Time), elapsed timer, volume bars, damage bars, mode (Offense/Defense)

## Game mechanics

### Punch types

- **Satoshi (hero):** Offense when Binance/Coinbase BUY volume > SELL. Punch type is determined by **BUY volume as a percentage of max BUY volume** (across both exchanges). Left hand = Binance BUY %, right hand = Coinbase BUY %. Bands: **Jab** 1–20%, **Body** 21–40%, **Hook** 41–60%, **Cross** 61–80%, **Uppercut** 81–100%. Each punch type has a cooldown (Jab 1s up to Uppercut 5s).
- **Lizard (villain):** Same logic driven by **SELL volume** (Binance/Coinbase sell volume % of max sell volume). Left/right hand by exchange; same percentage bands and cooldowns.

### Block / defense types

- **When in defense mode** (SELL > BUY for that side), the boxer shows a block or dodge. Defense type is derived from **BUY volume percentages** (Binance BUY % and Coinbase BUY % of their respective max):
  - **Head Block:** highest of (Binance BUY %, Coinbase BUY %) is between **57–100%**.
  - **Body Block:** highest BUY % between **24–56%**.
  - **Dodge Left:** Binance BUY % between **1–23%**.
  - **Dodge Right:** Coinbase BUY % between **1–23%**.
- **Which defense blocks which attack:** Jab/Body require **Dodge Right** (if punch is left hand) or **Dodge Left** (if right hand). Hook/Cross require **Body Block**. Uppercut requires **Head Block**. If the defender is not in the correct defense when the punch lands, damage is applied.

### Damage bar and KO animation

- Each boxer has a **damage bar** from 0 to **100** (MAX_DAMAGE_POINTS). It is shown in the overlay (e.g. next to Binance for Satoshi, Coinbase for Lizard).
- When a punch **lands** (opponent not defending with the correct type), **damage points** are added: Jab 1, Body 3, Hook 4, Cross 5, Uppercut 8.
- **When damage points reach 100**, the **KO sequence** is triggered: **Fall** (400 ms) → **Knocked Down** (5 s) → **Rise** (4.6 s) → back to idle. The damage bar is reset to 0 after the KO sequence. Punch/defense updates are paused during KO.

### Block height (Time) and elapsed timer

- **Time label:** In the overlay (center top), **"Time"** is the label for **Bitcoin block height** (tip height from Mempool.space). The numeric value shown is the current block height; if unavailable it shows "—".
- **Source and refresh:** Block height is polled via **Mempool.space** every **60 seconds**. The displayed value updates when the API returns a new height.
- **Last-block-update timer:** A separate **elapsed timer** tracks **time since the last block height change**. It is displayed under the block height as **HH:MM:SS** (hours, minutes, seconds). The timer is **reset only when the block height value actually changes** (not on every poll). So after each new block, the elapsed time goes back to 00:00:00 and then counts up until the next block is seen.
- **Visual feedback:** When the block height value changes, the number **flashes white** 10 times (40 ms on/off half-cycles). When the elapsed time **exceeds 10 minutes** (no new block seen), the elapsed timer text **flashes white 3 times every 30 seconds** to draw attention (stale block indicator).

## Technical details

- **Framework:** Jetpack Compose
- **Language:** Kotlin
- **Minimum SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)
- **Networking:** WebSocket for live Binance/Coinbase data; Retrofit/OkHttp available (e.g. for REST)

**Key components:**

- `MainActivity.kt` – UI, layer composition, boxing state (punch/defense/KO), damage, block height and timer, splash
- `WebSocketRepository.kt` – Binance and Coinbase WebSocket connections and parsed price/volume
- `PriceRepository.kt` – REST/block tip (e.g. Mempool.space block height)
- `PriceDisplay.kt` – Overlay price, volume bars, mode label, damage bar
- `BtcCandleChart.kt` – Optional BTC candle chart

## Getting started

### Prerequisites

- Android Studio (Hedgehog or later recommended)
- Android SDK 24 or higher
- Gradle and Kotlin versions as defined in the project

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/BTC_PunchUp.git
   cd BTC_PunchUp
   ```
2. Open the project in Android Studio.
3. Sync Gradle, then build and run on a device or emulator.

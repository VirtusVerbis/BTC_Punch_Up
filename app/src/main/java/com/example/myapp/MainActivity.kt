package com.example.myapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.toSize
import com.example.myapp.data.PriceRepository
import com.example.myapp.data.WebSocketRepository
import com.example.myapp.ui.BtcCandleChart
import com.example.myapp.ui.PriceDisplay
import com.example.myapp.ui.PulseDirection
import com.example.myapp.ui.theme.MyAppTheme
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

// Flag to enable/disable Binance and Coinbase logcat logs
const val ENABLE_EXCHANGE_LOGS = false

// Flag to enable/disable punch sequence logcat logs
const val ENABLE_PUNCH_LOGS = false  // Set to true to debug punch triggers

// Flag to enable/disable defense/block/dodge animation logcat logs
const val ENABLE_BLOCKING_LOGS = false  // Set to true to debug dodge/block triggers

// Flag to enable LizardDebug / agentLog output (logcat and debug file)
const val ENABLE_LIZARD_LOGS = false  // Set to true to enable LizardDebug / agentLog output

// Toggle dodging (defense animations) for testing - false = no block/dodge animations
const val ENABLE_DODGING = true

// Toggle punching for testing - false = no punch animations (Satoshi idle or defense only)
const val ENABLE_PUNCHING = true

// Splash screen: display duration and fade-out animation
const val SPLASH_DISPLAY_MS = 2500L   // 3 seconds default; skip on first touch
const val SPLASH_FADE_OUT_MS = 300    // fade-out duration in ms

// Satoshi sprite scale multiplier (adjustable for testing)
const val SATOSHI_SCALE = 3.5f  // 1.0 = 100% size, 1.5 = 150% size, 0.5 = 50% size

// Satoshi Y position factor (adjustable for testing)
// 0.0 = top of screen, 0.5 = center, 1.0 = bottom of screen
const val SATOSHI_Y_POSITION = 0.70f // 0.65f

// Satoshi X position factor (adjustable for testing)
// 0.0 = left edge, 0.5 = center, 1.0 = right edge
const val SATOSHI_X_POSITION = 0.80f //0.6f //0.525f

// Lizard (villain) sprite constants
const val LIZARD_SCALE = 5f
const val LIZARD_Y_POSITION = 0.66f //0.64f  // 0.57f
const val LIZARD_X_POSITION =  0.95f // 0.9f //0.765f

// Ring rotation (bg0): test mode and direction
const val TEST_RING_ROTATION = true  // true = use timer; false = trigger on Bitcoin block height update
enum class RingRotateDirection { Left, Right, Random }
val RING_ROTATE_DIRECTION = RingRotateDirection.Random  // Left, Right, or Random (default)
const val RING_ROTATE_FREQUENCY_MS =  2*60*1_000L  // When TEST_RING_ROTATION: rotate every N ms (e.g. 10 seconds)
const val RING_ROTATION_FRAME_DELAY_MS = 8000L //80L  // ms per ring rotation frame; lower = faster, higher = slower

// Cat walk across screen (fg3, in front of Satoshi)
const val SPAWN_CAT = true  // true = spawn on timer; false = spawn on block height update
const val CAT_SPAWN_Y_FACTOR = 0.965f  // Spawn Y = screenHeight * this (0.80 = near bottom, below Satoshi)
const val CAT_SPEED = 8f  // Pixels per frame (movement per animation tick; ~100 px/s at 80 ms/frame)
const val CAT_SPAWN_INTERVAL_MS =   15*60*1_000L  //15_000L  // When SPAWN_CAT true: spawn interval (ms)
const val CAT_SIZE_DP = 64f  // Cat sprite size (dp)
const val CAT_OFFSCREEN_MARGIN_PX = 50f  // Extra pixels beyond screen edge for spawn/despawn
private val E_CAT_LEFT_FRAMES = listOf(R.drawable.e_cat_left_1, R.drawable.e_cat_left_2)   // walks right-to-left
private val E_CAT_RIGHT_FRAMES = listOf(R.drawable.e_cat_right_1, R.drawable.e_cat_right_2) // walks left-to-right

// Background layers (farthest to closest): bg3, bg2, bg1 (chart), bg0 (ring)
const val AUDIENCE_FRAME_DELAY_MS = 4000L  //8000L  // 1 second per frame (bg3 audience)
// bg3 audience: 8 sets of 3 frames, synced to ring; set = ring frame index (0-7), frame in set = 0->1->2->0
private val AUDIENCE_FRAMES_BY_RING: List<List<Int>> = listOf(
    listOf(R.drawable.audience_0_r0, R.drawable.audience_1_r0, R.drawable.audience_2_r0),
    listOf(R.drawable.audience_0_r1, R.drawable.audience_1_r1, R.drawable.audience_2_r1),
    listOf(R.drawable.audience_0_r2, R.drawable.audience_1_r2, R.drawable.audience_2_r2),
    listOf(R.drawable.audience_0_r3, R.drawable.audience_1_r3, R.drawable.audience_2_r3),
    listOf(R.drawable.audience_0_r4, R.drawable.audience_1_r4, R.drawable.audience_2_r4),
    listOf(R.drawable.audience_0_r5, R.drawable.audience_1_r5, R.drawable.audience_2_r5),
    listOf(R.drawable.audience_0_r6, R.drawable.audience_1_r6, R.drawable.audience_2_r6),
    listOf(R.drawable.audience_0_r7, R.drawable.audience_1_r7, R.drawable.audience_2_r7)
)
private val BACKGROUND_LAYER_2_FRAMES = emptyList<Int>()
// bg2 signs: buy_btc_sign (5 frames, 0->1->2->3->4->Kill); spawn 1-3 per wave; cleared when ring frame changes
private val BUY_BTC_SIGN_FRAMES = listOf(
    R.drawable.buy_btc_sign_0, R.drawable.buy_btc_sign_1, R.drawable.buy_btc_sign_2, R.drawable.buy_btc_sign_3, R.drawable.buy_btc_sign_4
)
const val SIGN_FRAME_DELAY_MS = 600L
const val SIGN_SPAWN_INTERVAL_MS =  60_000L
const val SIGN_ROW_Y_FRACTION_1 = 0.20f
const val SIGN_ROW_Y_FRACTION_2 = 0.32f
const val SIGN_ROW_Y_FRACTION_3 = 0.42f
const val SIGN_SIZE_DP = 82f //48f
const val SIGN_MARGIN_X_FRACTION = 0.05f
// Ring: ring_0.png–ring_7.png (8 files); frame 0 = idle, 1–7 = rotation (right 1..7, left 7..1)
private val RING_FRAMES = listOf(
    R.drawable.ring_0, R.drawable.ring_1, R.drawable.ring_2, R.drawable.ring_3, R.drawable.ring_4,
    R.drawable.ring_5, R.drawable.ring_6, R.drawable.ring_7
)

// Bobbing movement (boxers move left/right gradually, in opposite directions)
const val BOBBING_MAX_X_LEFT = -20f   // Max pixels left of center
const val BOBBING_MAX_X_RIGHT = 20f   // Max pixels right of center
const val BOBBING_MAX_Y_UP = -20f     // Max pixels up from center
const val BOBBING_MAX_Y_DOWN = 20f    // Max pixels down from center
const val BOBBING_STEP_PX = 1f // 0.5f      // Pixels per tick
const val BOBBING_INTERVAL_MS = 40L   // ms between updates
const val BOBBING_Y_STEPS_PER_FULL_X_CYCLE = 1  // Y increments applied per full Left-Right or Right-Left cycle

// Depth scale (boxers appear smaller when up, larger when down) – per sprite for tuning
const val SCALE_SMALLER_PERCENT_SATOSHI = 12f //30f
const val SCALE_LARGER_PERCENT_SATOSHI = 60f //40f // 30f
const val SCALE_SMALLER_PERCENT_LIZARD = 20f //20f
const val SCALE_LARGER_PERCENT_LIZARD = 30f //24f //20f

// Spread-based defense mode constants
// Rolling window length for median spread calculation (seconds); default 60L
const val SPREAD_MEDIAN_WINDOW_SECONDS = 10L

// Trigger defense when current spread > medianSpread * this multiplier; default 3.0f
const val SPREAD_DEFENSE_MULTIPLIER = 0.25f

// Ignore tiny spreads below this floor (percentage of mid-price); default 0.05f
const val SPREAD_DEFENSE_MIN_PERCENT = 0.001f  // 0.05% = 5 basis points

// Animation frame delay (milliseconds per frame)
const val ANIMATION_FRAME_DELAY_MS = 80L  // 100ms = ~10 FPS for sprite animations

// WebSocket/UI throttle (milliseconds between ExchangeData emissions)
// Why 100ms: close to the 80ms animation frame delay, but caps exchange-driven state changes to ~10 updates/sec.
// This reduces Compose recomposition / LaunchedEffect restart churn during high market activity (which can cancel animations).
// Tuning:
// - Lower bound: ~80ms (below animation frame delay usually adds churn without visible benefit)
// - Typical: 80–150ms
// - If glitches persist under heavy input: try 200–250ms
const val EXCHANGE_EMIT_THROTTLE_MS = 100L

// BG2 candle chart (top-half chart, shown periodically)
const val BG2_SHOW_INTERVAL_MS =  3*60*1_000L //60_000L       // How often to show the chart (once per minute)
const val BG2_VISIBLE_DURATION_MS = 30_000L //15_000L   // How long the chart stays visible
const val BG2_MAX_CANDLES = 200 //60                 // Max 1-min candles to keep in memory; older dropped
const val BG2_CANDLE_EMIT_THROTTLE_MS = 500L  // Min ms between emitting candle list to UI
const val BG2_SHOW_AXIS_LABELS = true        // When true, show Y (price) and X (time) axis labels on the chart
const val BG2_CHART_TOP_OFFSET_FRACTION = 0.15f // Fraction of screen height from top before chart starts; increase to move chart lower
const val BG2_CHART_HEIGHT_FRACTION = 0.495f  // Fraction of screen height the chart band occupies

const val DAMAGE_DEBUG = false  // set true to enable damage debug logs
// Fallback timeout if onPlayOnceComplete is never invoked (e.g. effect cancelled); clears damage state so app cannot get stuck
const val DAMAGE_COMPLETION_SAFETY_TIMEOUT_MS = 3000L

// KO phase display time (ms) for single-frame Fall / Knocked Down / Rise; used by both Satoshi and Lizard
const val KO_FALL_DISPLAY_MS = 400L
const val KO_KNOCKED_DOWN_DISPLAY_MS = 5000L
const val KO_RISE_DISPLAY_MS = 4600L

// Damage points per punch type (adjustable for testing KO animation faster)
const val DAMAGE_POINTS_JAB = 1
const val DAMAGE_POINTS_BODY = 3
const val DAMAGE_POINTS_HOOK = 4
const val DAMAGE_POINTS_CROSS = 5
const val DAMAGE_POINTS_UPPERCUT = 8
// KO threshold and damage bar cap (lower for faster KO testing)
const val MAX_DAMAGE_POINTS = 100 // 100

// Bitcoin block height (Mempool) refresh interval (ms); once per minute
const val BLOCK_HEIGHT_REFRESH_INTERVAL_MS = 60_000L
// Half-cycle duration (ms) for block height update flash; 10 flashes = 20 half-cycles
const val BLOCK_HEIGHT_FLASH_HALF_MS = 40L
// When elapsed timer exceeds 10 min, flash 3 times every 30 seconds
const val TIMER_FLASH_WHEN_ELAPSED_MS = 600_000L
const val TIMER_FLASH_INTERVAL_MS = 30_000L

// Volume percentage thresholds for punch types (adjustable)
// Punch type is determined by volume percentage of max volume
// These thresholds apply to both left (Binance) and right (Coinbase) hands
const val VOLUME_PERCENT_JAB_MIN = 0.01f      // 1% = minimum for Jab
const val VOLUME_PERCENT_JAB_MAX = 0.20f      // 1-20% = Jab
const val VOLUME_PERCENT_BODY_MIN = 0.21f     // 21-40% = Body
const val VOLUME_PERCENT_BODY_MAX = 0.40f
const val VOLUME_PERCENT_HOOK_MIN = 0.41f     // 41-60% = Hook
const val VOLUME_PERCENT_HOOK_MAX = 0.60f
const val VOLUME_PERCENT_CROSS_MIN = 0.61f    // 61-80% = Cross
const val VOLUME_PERCENT_CROSS_MAX = 0.80f
const val VOLUME_PERCENT_UPPERCUT_MIN = 0.81f // 81-100% = Uppercut
const val VOLUME_PERCENT_UPPERCUT_MAX = 1.00f

// Volume percentage thresholds for defense types (adjustable)
// Defense type is determined by BUY volume percentage of max BUY volume
// Head Block: Both Binance and Coinbase BUY volume % between 67-100%
const val DEFENSE_HEAD_BLOCK_MIN = 0.57f  //0.67
const val DEFENSE_HEAD_BLOCK_MAX = 1.00f

// Body Block: Both Binance and Coinbase BUY volume % between 34-66%
const val DEFENSE_BODY_BLOCK_MIN = 0.24f  //0.24
const val DEFENSE_BODY_BLOCK_MAX = 0.56f  //0.66

// Dodge Left: Binance BUY volume % between 1-33%
const val DEFENSE_DODGE_LEFT_MIN = 0.01f
const val DEFENSE_DODGE_LEFT_MAX = 0.23f   //0.23

// Dodge Right: Coinbase BUY volume % between 1-33%
const val DEFENSE_DODGE_RIGHT_MIN = 0.01f
const val DEFENSE_DODGE_RIGHT_MAX = 0.23f  //0.23

// Defense cooldown durations (milliseconds) - per defense type
// Minimum time to keep showing current defense before allowing switch
const val DEFENSE_HEAD_BLOCK_COOLDOWN_MS = 1000L   // 1 second
const val DEFENSE_BODY_BLOCK_COOLDOWN_MS = 1000L   // 1 second
const val DEFENSE_DODGE_LEFT_COOLDOWN_MS = 1000L   // 1 second
const val DEFENSE_DODGE_RIGHT_COOLDOWN_MS = 1000L  // 1 second

// Minimum idle time (ms) after defense animation completes before re-entering defense
const val MIN_IDLE_AFTER_DEFENSE_MS = 100L//300L

// Punch priority hand (configurable - determines which hand executes when both are active)
// RIGHT = Coinbase, LEFT = Binance
val PUNCH_PRIORITY_HAND = HandSide.RIGHT

// Punch cooldown durations (milliseconds) - per punch type
const val PUNCH_COOLDOWN_JAB_MS = 1000L      // 1 second
const val PUNCH_COOLDOWN_BODY_MS = 2000L     // 2 seconds
const val PUNCH_COOLDOWN_HOOK_MS = 3000L     // 3 seconds
const val PUNCH_COOLDOWN_CROSS_MS = 4000L    // 4 seconds
const val PUNCH_COOLDOWN_UPPERCUT_MS = 5000L // 5 seconds

// Satoshi idle animation frame resources
val SATOSHI_IDLE_FRAMES = listOf(
    R.drawable.satoshi_ready_0,
    R.drawable.satoshi_ready_1,
    R.drawable.satoshi_ready_2,
    R.drawable.satoshi_ready_3,
    R.drawable.satoshi_ready_4,
    R.drawable.satoshi_ready_5
)

// Lizard (villain) idle animation frames
val LIZARD_IDLE_FRAMES = listOf(
    R.drawable.lizard_idle_0,
    R.drawable.lizard_idle_1,
    R.drawable.lizard_idle_2,
    R.drawable.lizard_idle_3,
    R.drawable.lizard_idle_4,
    R.drawable.lizard_idle_5
)

// Lizard punch and defense frames (placeholders - PNGs to be supplied)
val LIZARD_HEAD_BLOCK_FRAMES = listOf(
    R.drawable.lizard_block_head_0,
    R.drawable.lizard_block_head_1,
    R.drawable.lizard_block_head_2
)
val LIZARD_BODY_BLOCK_FRAMES = listOf(
    R.drawable.lizard_block_body_0,
    R.drawable.lizard_block_body_1,
    R.drawable.lizard_block_body_2
)
val LIZARD_DODGE_LEFT_FRAMES = listOf(
    R.drawable.lizard_left_dodge_0,
    R.drawable.lizard_left_dodge_1,
    R.drawable.lizard_left_dodge_2
)
val LIZARD_DODGE_RIGHT_FRAMES = listOf(
    R.drawable.lizard_right_dodge_0,
    R.drawable.lizard_right_dodge_1,
    R.drawable.lizard_right_dodge_2
)
val LIZARD_LEFT_JAB_FRAMES = listOf(
    R.drawable.lizard_left_jab_0,
    R.drawable.lizard_left_jab_1,
    R.drawable.lizard_left_jab_2
)
val LIZARD_LEFT_BODY_FRAMES = listOf(
    R.drawable.lizard_left_body_0,
    R.drawable.lizard_left_body_1,
    R.drawable.lizard_left_body_2
)
val LIZARD_LEFT_HOOK_FRAMES = listOf(
    R.drawable.lizard_left_hook_0,
    R.drawable.lizard_left_hook_1,
    R.drawable.lizard_left_hook_2,
    R.drawable.lizard_left_hook_3
)
val LIZARD_LEFT_CROSS_FRAMES = listOf(
    R.drawable.lizard_left_cross_0,
    R.drawable.lizard_left_cross_1,
    R.drawable.lizard_left_cross_2
)
val LIZARD_LEFT_UPPERCUT_FRAMES = listOf(
    R.drawable.lizard_left_uppercut_0,
    R.drawable.lizard_left_uppercut_1,
    R.drawable.lizard_left_uppercut_2,
    R.drawable.lizard_left_uppercut_3
)
val LIZARD_RIGHT_JAB_FRAMES = listOf(
    R.drawable.lizard_right_jab_0,
    R.drawable.lizard_right_jab_1,
    R.drawable.lizard_right_jab_2
)
val LIZARD_RIGHT_BODY_FRAMES = listOf(
    R.drawable.lizard_right_body_0,
    R.drawable.lizard_right_body_1,
    R.drawable.lizard_right_body_2
)
val LIZARD_RIGHT_HOOK_FRAMES = listOf(
    R.drawable.lizard_right_hook_0,
    R.drawable.lizard_right_hook_1,
    R.drawable.lizard_right_hook_2,
    R.drawable.lizard_right_hook_3
)
val LIZARD_RIGHT_CROSS_FRAMES = listOf(
    R.drawable.lizard_right_cross_0,
    R.drawable.lizard_right_cross_1,
    R.drawable.lizard_right_cross_2
)
val LIZARD_RIGHT_UPPERCUT_FRAMES = listOf(
    R.drawable.lizard_right_uppercut_0,
    R.drawable.lizard_right_uppercut_1,
    R.drawable.lizard_right_uppercut_2,
    R.drawable.lizard_right_uppercut_3
)

// Lizard damage animation frames (head)
val LIZARD_LEFT_DAMAGE_HEAD_FRAMES = listOf(
    R.drawable.lizard_left_damage_head_0,
    R.drawable.lizard_left_damage_head_1,
    R.drawable.lizard_left_damage_head_2
)
val LIZARD_RIGHT_DAMAGE_HEAD_FRAMES = listOf(
    R.drawable.lizard_right_damage_head_0,
    R.drawable.lizard_right_damage_head_1,
    R.drawable.lizard_right_damage_head_2
)

// Lizard damage animation frames (small head - JAB only)
val LIZARD_LEFT_SMALL_DAMAGE_HEAD_FRAMES = listOf(
    R.drawable.lizard_left_small_head_dmg_0,
    R.drawable.lizard_left_small_head_dmg_1,
    R.drawable.lizard_left_small_head_dmg_2
)
val LIZARD_RIGHT_SMALL_DAMAGE_HEAD_FRAMES = listOf(
    R.drawable.lizard_right_small_head_dmg_0,
    R.drawable.lizard_right_small_head_dmg_1,
    R.drawable.lizard_right_small_head_dmg_2
)

// Lizard damage animation frames (body)
val LIZARD_LEFT_DAMAGE_BODY_FRAMES = listOf(
    R.drawable.lizard_left_body_dmg_0,
    R.drawable.lizard_left_body_dmg_1,
    R.drawable.lizard_left_body_dmg_2
)
val LIZARD_RIGHT_DAMAGE_BODY_FRAMES = listOf(
    R.drawable.lizard_right_body_dmg_0,
    R.drawable.lizard_right_body_dmg_1,
    R.drawable.lizard_right_body_dmg_2
)
// Lizard damage animation frames (center uppercut)
val LIZARD_CENTER_DAMAGE_UPPERCUT_FRAMES = listOf(
    R.drawable.lizard_damage_center_0,
    R.drawable.lizard_damage_center_1,
    R.drawable.lizard_damage_center_2,
    R.drawable.lizard_damage_center_3
)

// KO sequence frames (Satoshi and Lizard: real KO assets)
val SATOSHI_FALL_FRAMES = listOf(R.drawable.satoshi_falling_0)
val SATOSHI_KNOCKED_DOWN_FRAMES = listOf(R.drawable.satoshi_knocked_down_0)
val SATOSHI_RISE_FRAMES = listOf(R.drawable.satoshi_rising_0)
val LIZARD_FALL_FRAMES = listOf(R.drawable.lizard_falling_0)
val LIZARD_KNOCKED_DOWN_FRAMES = listOf(R.drawable.lizard_knocked_down_0)
val LIZARD_RISE_FRAMES = listOf(R.drawable.lizard_rising_0)

fun getSatoshiKOPhaseFrames(phase: KOPhase): List<Int> = when (phase) {
    KOPhase.FALL -> SATOSHI_FALL_FRAMES
    KOPhase.KNOCKED_DOWN -> SATOSHI_KNOCKED_DOWN_FRAMES
    KOPhase.RISE -> SATOSHI_RISE_FRAMES
}

fun getLizardKOPhaseFrames(phase: KOPhase): List<Int> = when (phase) {
    KOPhase.FALL -> LIZARD_FALL_FRAMES
    KOPhase.KNOCKED_DOWN -> LIZARD_KNOCKED_DOWN_FRAMES
    KOPhase.RISE -> LIZARD_RISE_FRAMES
}

// Idle animation loop duration (6 frames × 100ms = 600ms)
val IDLE_LOOP_DURATION_MS = SATOSHI_IDLE_FRAMES.size * ANIMATION_FRAME_DELAY_MS

// Satoshi punch animation frames
// Right hand punch animations (Coinbase BUY volume)
val SATOSHI_RIGHT_JAB_FRAMES = listOf(
    R.drawable.satoshi_right_jab_0,
    R.drawable.satoshi_right_jab_1,
    R.drawable.satoshi_right_jab_2
)

val SATOSHI_RIGHT_BODY_FRAMES = listOf(
    R.drawable.satoshi_right_body_0,
    R.drawable.satoshi_right_body_1,
    R.drawable.satoshi_right_body_2
)

val SATOSHI_RIGHT_HOOK_FRAMES = listOf(
    R.drawable.satoshi_right_hook_0,
    R.drawable.satoshi_right_hook_1,
    R.drawable.satoshi_right_hook_2
)

val SATOSHI_RIGHT_CROSS_FRAMES = listOf(
    R.drawable.satoshi_right_cross_0,
    R.drawable.satoshi_right_cross_1,
    R.drawable.satoshi_right_cross_2
)

val SATOSHI_RIGHT_UPPERCUT_FRAMES = listOf(
    R.drawable.satoshi_right_uppercut_0,
    R.drawable.satoshi_right_uppercut_1,
    R.drawable.satoshi_right_uppercut_2,
    R.drawable.satoshi_right_uppercut_3
)

// Left hand punch animations (Binance BUY volume)
val SATOSHI_LEFT_JAB_FRAMES = listOf(
    R.drawable.satoshi_left_jab_0,
    R.drawable.satoshi_left_jab_1,
    R.drawable.satoshi_left_jab_2
)

val SATOSHI_LEFT_BODY_FRAMES = listOf(
    R.drawable.satoshi_left_body_0,
    R.drawable.satoshi_left_body_1,
    R.drawable.satoshi_left_body_2
)

val SATOSHI_LEFT_HOOK_FRAMES = listOf(
    R.drawable.satoshi_left_hook_0,
    R.drawable.satoshi_left_hook_1,
    R.drawable.satoshi_left_hook_2
)

val SATOSHI_LEFT_CROSS_FRAMES = listOf(
    R.drawable.satoshi_left_cross_0,
    R.drawable.satoshi_left_cross_1,
    R.drawable.satoshi_left_cross_2
)

val SATOSHI_LEFT_UPPERCUT_FRAMES = listOf(
    R.drawable.satoshi_left_uppercut_0,
    R.drawable.satoshi_left_uppercut_1,
    R.drawable.satoshi_left_uppercut_2,
    R.drawable.satoshi_left_uppercut_3
)

// Satoshi defense animation frames
// Head Block
val SATOSHI_HEAD_BLOCK_FRAMES = listOf(
    R.drawable.satoshi_block_head_0,
    R.drawable.satoshi_block_head_1,
    R.drawable.satoshi_block_head_2
)

// Body Block
val SATOSHI_BODY_BLOCK_FRAMES = listOf(
    R.drawable.satoshi_block_body_0,
    R.drawable.satoshi_block_body_1,
    R.drawable.satoshi_block_body_2
)

// Dodge Left
val SATOSHI_DODGE_LEFT_FRAMES = listOf(
    R.drawable.satoshi_left_dodge_0,
    R.drawable.satoshi_left_dodge_1,
    R.drawable.satoshi_left_dodge_2
)

// Dodge Right
val SATOSHI_DODGE_RIGHT_FRAMES = listOf(
    R.drawable.satoshi_right_dodge_0,
    R.drawable.satoshi_right_dodge_1,
    R.drawable.satoshi_right_dodge_2
)

// Satoshi damage animation frames (head)
val SATOSHI_LEFT_DAMAGE_HEAD_FRAMES = listOf(
    R.drawable.satoshi_left_dmg_head_0,
    R.drawable.satoshi_left_dmg_head_1,
    R.drawable.satoshi_left_dmg_head_2
)
val SATOSHI_RIGHT_DAMAGE_HEAD_FRAMES = listOf(
    R.drawable.satoshi_right_dmg_head_0,
    R.drawable.satoshi_right_dmg_head_1,
    R.drawable.satoshi_right_dmg_head_2
)

// Satoshi damage animation frames (center head / uppercut)
val SATOSHI_CENTER_DAMAGE_UPPERCUT_FRAMES = listOf(
    R.drawable.satoshi_dmg_head_0,
    R.drawable.satoshi_dmg_head_1,
    R.drawable.satoshi_dmg_head_2,
    R.drawable.satoshi_dmg_head_3,
)

// Satoshi damage animation frames (body)
val SATOSHI_LEFT_DAMAGE_BODY_FRAMES = listOf(
    R.drawable.satoshi_left_dmg_body_0,
    R.drawable.satoshi_left_dmg_body_1,
    R.drawable.satoshi_left_dmg_body_2
)
val SATOSHI_RIGHT_DAMAGE_BODY_FRAMES = listOf(
    R.drawable.satoshi_right_dmg_body_0,
    R.drawable.satoshi_right_dmg_body_1,
    R.drawable.satoshi_right_dmg_body_2
)

// Shared state for sprite position
class SpriteState {
    var position: Offset = Offset.Zero
}

// Sprite type enum (to be updated for boxing theme)
enum class SpriteType {
    SATOSHI,  // Hero boxer
    LIZARD,   // Villain boxer
    CAT       // Walk across fg3 (on top of Satoshi)
}

// Punch attack types
enum class PunchType {
    JAB,
    BODY,
    HOOK,
    CROSS,
    UPPERCUT
}

// Hand side for punches
enum class HandSide {
    LEFT,   // Binance BUY volume
    RIGHT   // Coinbase BUY volume
}

// Defense types
enum class DefenseType {
    HEAD_BLOCK,
    BODY_BLOCK,
    DODGE_LEFT,
    DODGE_RIGHT
}

// Damage animation types (PNG frames to be provided later)
enum class DamageAnimationType {
    LEFT_DAMAGE_HEAD,
    RIGHT_DAMAGE_HEAD,
    LEFT_SMALL_DAMAGE_HEAD,
    RIGHT_SMALL_DAMAGE_HEAD,
    LEFT_DAMAGE_BODY,
    RIGHT_DAMAGE_BODY,
    CENTER_DAMAGE_UPPERCUT
}

// bg2 signs: one spawn instance (0->1->2->Kill); rowIndex 0..2 = which Y row
data class BtcSignSpawn(
    val id: Long,
    val xPx: Float,
    val yPx: Float,
    val frameIndex: Int,
    val rowIndex: Int
)

// Pending impact check: run hit detection once at 2nd-last frame of punch
data class ImpactCheckData(
    val punchType: PunchType,
    val handSide: HandSide,
    val frameCount: Int
)

// Sprite data structure (simplified for boxing theme)
data class SpriteData(
    val spriteState: SpriteState,
    val spriteResourceId: Int,  // For static sprites or current frame
    val spriteType: SpriteType,
    val layer: Int = 2,  // 1=fg1(Lizard), 2=fg2(Satoshi), 3=fg3(Cat)
    val sizeScale: Float = 1.0f, // Current size multiplier (default 1.0 = 100%)
    val spriteSizeDp: Float = 64f, // Sprite size in dp (default 64dp, Satoshi uses 128dp)
    val animationFrames: List<Int> = emptyList(),  // List of drawable resource IDs for animation
    val currentFrameIndex: Int = 0,  // Current frame in animation sequence
    val isAnimated: Boolean = false,  // Whether this sprite uses animation
    val currentPunchType: PunchType? = null,  // Current punch being performed (null = idle)
    val currentHandSide: HandSide? = null,    // Which hand is punching (null = idle)
    val isPunching: Boolean = false,           // Whether currently performing a punch
    val playAnimationOnce: Boolean = false,    // If true, play animation once (e.g. defense); if false, loop (e.g. idle)
    val currentDefenseType: DefenseType? = null, // Current defense animation being performed (null = not defending)
    val isDefending: Boolean = false            // Whether currently performing a defense animation
)

// Helper functions for punch cooldown management
fun getPunchCooldownMs(punchType: PunchType): Long {
    return when (punchType) {
        PunchType.JAB -> PUNCH_COOLDOWN_JAB_MS
        PunchType.BODY -> PUNCH_COOLDOWN_BODY_MS
        PunchType.HOOK -> PUNCH_COOLDOWN_HOOK_MS
        PunchType.CROSS -> PUNCH_COOLDOWN_CROSS_MS
        PunchType.UPPERCUT -> PUNCH_COOLDOWN_UPPERCUT_MS
    }
}

fun isPunchOnCooldown(punchType: PunchType, lastPunchTime: Map<PunchType, Long>): Boolean {
    val lastTime = lastPunchTime[punchType] ?: return false
    val cooldownMs = getPunchCooldownMs(punchType)
    val currentTime = System.currentTimeMillis()
    return (currentTime - lastTime) < cooldownMs
}

fun areAllPunchesOnCooldown(lastPunchTime: Map<PunchType, Long>): Boolean {
    // Only check punch types that have been executed at least once
    // If no punches have been executed, return false (not all on cooldown)
    if (lastPunchTime.isEmpty()) return false
    
    // Check if all executed punch types are still on cooldown
    return lastPunchTime.keys.all { punchType ->
        isPunchOnCooldown(punchType, lastPunchTime)
    }
}

// Helper functions for defense cooldown management
fun getDefenseCooldownMs(defenseType: DefenseType): Long {
    return when (defenseType) {
        DefenseType.HEAD_BLOCK -> DEFENSE_HEAD_BLOCK_COOLDOWN_MS
        DefenseType.BODY_BLOCK -> DEFENSE_BODY_BLOCK_COOLDOWN_MS
        DefenseType.DODGE_LEFT -> DEFENSE_DODGE_LEFT_COOLDOWN_MS
        DefenseType.DODGE_RIGHT -> DEFENSE_DODGE_RIGHT_COOLDOWN_MS
    }
}

fun isDefenseOnCooldown(currentDefenseType: DefenseType?, lastDefenseSwitchTime: Long): Boolean {
    if (currentDefenseType == null) return false
    val cooldownMs = getDefenseCooldownMs(currentDefenseType)
    val currentTime = System.currentTimeMillis()
    return (currentTime - lastDefenseSwitchTime) < cooldownMs
}

// Damage system: which defense successfully blocks/evades which attack
fun getRequiredDefenseForAttack(punchType: PunchType, handSide: HandSide): DefenseType {
    return when (punchType) {
        PunchType.JAB, PunchType.BODY -> if (handSide == HandSide.LEFT) DefenseType.DODGE_RIGHT else DefenseType.DODGE_LEFT
        PunchType.HOOK, PunchType.CROSS -> DefenseType.BODY_BLOCK
        PunchType.UPPERCUT -> DefenseType.HEAD_BLOCK
    }
}

// Damage points per punch type (for damage bar 0-MAX_DAMAGE_POINTS)
fun getDamagePoints(punchType: PunchType): Int = when (punchType) {
    PunchType.JAB -> DAMAGE_POINTS_JAB
    PunchType.BODY -> DAMAGE_POINTS_BODY
    PunchType.HOOK -> DAMAGE_POINTS_HOOK
    PunchType.CROSS -> DAMAGE_POINTS_CROSS
    PunchType.UPPERCUT -> DAMAGE_POINTS_UPPERCUT
}

// KO sequence phase (placeholders use idle so each phase completes and returns to idle)
enum class KOPhase { FALL, KNOCKED_DOWN, RISE }

// Damage system: which damage animation type to show for a given attack
fun getDamageTypeForAttack(punchType: PunchType, handSide: HandSide): DamageAnimationType {
    return when (punchType) {
        PunchType.JAB -> if (handSide == HandSide.LEFT) DamageAnimationType.LEFT_SMALL_DAMAGE_HEAD else DamageAnimationType.RIGHT_SMALL_DAMAGE_HEAD
        PunchType.BODY -> if (handSide == HandSide.LEFT) DamageAnimationType.RIGHT_DAMAGE_BODY else DamageAnimationType.LEFT_DAMAGE_BODY
        PunchType.HOOK, PunchType.CROSS -> if (handSide == HandSide.LEFT) DamageAnimationType.RIGHT_DAMAGE_HEAD else DamageAnimationType.LEFT_DAMAGE_HEAD
        PunchType.UPPERCUT -> DamageAnimationType.CENTER_DAMAGE_UPPERCUT
    }
}

fun getSatoshiDamageFrames(type: DamageAnimationType): List<Int> = when (type) {
    DamageAnimationType.LEFT_DAMAGE_HEAD -> SATOSHI_LEFT_DAMAGE_HEAD_FRAMES
    DamageAnimationType.RIGHT_DAMAGE_HEAD -> SATOSHI_RIGHT_DAMAGE_HEAD_FRAMES
    DamageAnimationType.LEFT_SMALL_DAMAGE_HEAD -> SATOSHI_LEFT_DAMAGE_HEAD_FRAMES
    DamageAnimationType.RIGHT_SMALL_DAMAGE_HEAD -> SATOSHI_RIGHT_DAMAGE_HEAD_FRAMES
    DamageAnimationType.LEFT_DAMAGE_BODY -> SATOSHI_LEFT_DAMAGE_BODY_FRAMES
    DamageAnimationType.RIGHT_DAMAGE_BODY -> SATOSHI_RIGHT_DAMAGE_BODY_FRAMES
    DamageAnimationType.CENTER_DAMAGE_UPPERCUT -> SATOSHI_CENTER_DAMAGE_UPPERCUT_FRAMES
}

fun getLizardDamageFrames(type: DamageAnimationType): List<Int> = when (type) {
    DamageAnimationType.LEFT_DAMAGE_HEAD -> LIZARD_LEFT_DAMAGE_HEAD_FRAMES
    DamageAnimationType.RIGHT_DAMAGE_HEAD -> LIZARD_RIGHT_DAMAGE_HEAD_FRAMES
    DamageAnimationType.LEFT_SMALL_DAMAGE_HEAD -> LIZARD_LEFT_SMALL_DAMAGE_HEAD_FRAMES
    DamageAnimationType.RIGHT_SMALL_DAMAGE_HEAD -> LIZARD_RIGHT_SMALL_DAMAGE_HEAD_FRAMES
    DamageAnimationType.LEFT_DAMAGE_BODY -> LIZARD_LEFT_DAMAGE_BODY_FRAMES
    DamageAnimationType.RIGHT_DAMAGE_BODY -> LIZARD_RIGHT_DAMAGE_BODY_FRAMES
    DamageAnimationType.CENTER_DAMAGE_UPPERCUT -> LIZARD_CENTER_DAMAGE_UPPERCUT_FRAMES
}

// #region agent log
private const val DEBUG_SESSION_ID = "debug-session"
private const val DEBUG_RUN_ID = "run1"
private fun agentLog(location: String, message: String, dataStr: String, hypothesisId: String) {
    if (!ENABLE_LIZARD_LOGS) return
    val ts = System.currentTimeMillis()
    val line = """{"sessionId":"$DEBUG_SESSION_ID","runId":"$DEBUG_RUN_ID","location":"$location","message":"$message","data":$dataStr,"timestamp":$ts,"hypothesisId":"$hypothesisId"}"""
    Log.d("LizardDebug", line)
    try { File("d:\\BTC_PunchUp\\.cursor\\debug.log").appendText("$line\n") } catch (_: Exception) {}
}
// #endregion

class MainActivity : ComponentActivity() {
    private var webSocketRepository: WebSocketRepository? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyAppTheme {
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    SplashScreen(onDismiss = { showSplash = false })
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black
                    ) {
                        PriceDisplayScreen(
                            onRepositoryCreated = { repository ->
                                webSocketRepository = repository
                            }
                        )
                    }
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        webSocketRepository?.disconnect()
    }
    
    override fun onResume() {
        super.onResume()
        webSocketRepository?.connect()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        webSocketRepository?.cleanup()
        webSocketRepository = null
    }
}

@Composable
fun SplashScreen(onDismiss: () -> Unit) {
    var dismissRequested by remember { mutableStateOf(false) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(SPLASH_DISPLAY_MS)
        dismissRequested = true
    }
    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            alpha.animateTo(0f, tween(SPLASH_FADE_OUT_MS))
            onDismiss()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { if (!dismissRequested) dismissRequested = true }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha.value)
        ) {
            Image(
                painter = painterResource(R.drawable.vv_splash),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun PriceDisplayScreen(
    onRepositoryCreated: (WebSocketRepository) -> Unit = {}
) {
    val repository = remember { PriceRepository() }
    val webSocketRepository = remember { 
        WebSocketRepository().also { onRepositoryCreated(it) }
    }
    
    // WebSocket data
    val binanceWebSocketData by webSocketRepository.binanceData.collectAsState()
    val coinbaseWebSocketData by webSocketRepository.coinbaseData.collectAsState()

    // Defense when sell volume > buy volume; Offense otherwise (Option A: volume-based)
    val isBinanceDefense = (binanceWebSocketData.sellVolume ?: 0.0) > (binanceWebSocketData.buyVolume ?: 0.0)
    val isCoinbaseDefense = (coinbaseWebSocketData.sellVolume ?: 0.0) > (coinbaseWebSocketData.buyVolume ?: 0.0)
    
    // Binance state (use WebSocket data, fallback to polling if needed)
    var binancePrice by remember { mutableStateOf<Double?>(null) }
    var binancePreviousPrice by remember { mutableStateOf<Double?>(null) }
    var binanceIsConnected by remember { mutableStateOf(false) }
    var binanceBuyVolume by remember { mutableStateOf<Double?>(null) }
    var binanceSellVolume by remember { mutableStateOf<Double?>(null) }
    
    // Coinbase state (use WebSocket data, fallback to polling if needed)
    var coinbasePrice by remember { mutableStateOf<Double?>(null) }
    var coinbasePreviousPrice by remember { mutableStateOf<Double?>(null) }
    var coinbaseIsConnected by remember { mutableStateOf(false) }
    var coinbaseBuyVolume by remember { mutableStateOf<Double?>(null) }
    var coinbaseSellVolume by remember { mutableStateOf<Double?>(null) }
    
    // Flag to enable/disable WebSocket (set to false to use polling fallback)
    val useWebSocket = true
    
    // Volume normalization and animation
    var maxVolume by remember { mutableStateOf(1.0) }
    
    // Exchange-specific BUY volume tracking (for hero boxer punches)
    var maxBinanceBuyVolume by remember { mutableStateOf(0.0) }  // Historical max Binance BUY volume
    var maxCoinbaseBuyVolume by remember { mutableStateOf(0.0) }  // Historical max Coinbase BUY volume
    
    // Exchange-specific SELL volume tracking (for future villain boxer animations).
    // Intended villain logic (not yet implemented): mirror hero using SELL volume —
    // villain offense when sell > buy, defense otherwise; SELL % drives villain punch/block type.
    var maxBinanceSellVolume by remember { mutableStateOf(0.0) }  // Historical max Binance SELL volume
    var maxCoinbaseSellVolume by remember { mutableStateOf(0.0) }  // Historical max Coinbase SELL volume
    
    var binanceVolumeAnimating by remember { mutableStateOf(false) }
    var coinbaseVolumeAnimating by remember { mutableStateOf(false) }
    
    // Bitcoin block height (Mempool.space), refreshed every BLOCK_HEIGHT_REFRESH_INTERVAL_MS
    var blockHeight by remember { mutableStateOf<Int?>(null) }
    var blockHeightFlashOn by remember { mutableStateOf(false) }
    var lastBlockHeightUpdateTimeMs by remember { mutableStateOf<Long?>(null) }
    var tick by remember { mutableStateOf(0) }
    var timerOverTenMinFlashOn by remember { mutableStateOf(false) }
    var overTenMin by remember { mutableStateOf(false) }
    var backgroundFrameIndices by remember { mutableStateOf(listOf(0, 0, 0)) }
    var bg2Visible by remember { mutableStateOf(false) }
    var ringRotationTriggerCount by remember { mutableStateOf(0) }
    var isRingRotating by remember { mutableStateOf(false) }
    var catSpawnTriggerCount by remember { mutableStateOf(0) }
    var catDirectionLeft by remember { mutableStateOf<Boolean?>(null) }  // true = walking left, false = right; null = no cat
    var signSpawns by remember { mutableStateOf<List<BtcSignSpawn>>(emptyList()) }
    var lastRingFrameIndex by remember { mutableStateOf(0) }

    // BG2 chart: show once every BG2_SHOW_INTERVAL_MS for BG2_VISIBLE_DURATION_MS
    LaunchedEffect(Unit) {
        while (true) {
            delay(BG2_SHOW_INTERVAL_MS)
            bg2Visible = true
            delay(BG2_VISIBLE_DURATION_MS)
            bg2Visible = false
        }
    }

    val candleData by webSocketRepository.candleData.collectAsState()

    // Calculate max volume from all volumes
    val calculateMaxVolume: () -> Double = {
        val volumes = listOf(
            binanceBuyVolume ?: 0.0,
            binanceSellVolume ?: 0.0,
            coinbaseBuyVolume ?: 0.0,
            coinbaseSellVolume ?: 0.0
        )
        val maxVol = maxOf(volumes.maxOrNull() ?: 1.0, 1.0)
        if (ENABLE_EXCHANGE_LOGS) {
            Log.d("MainActivity", "Max Volume: $maxVol, Volumes: BinanceBuy=${binanceBuyVolume}, BinanceSell=${binanceSellVolume}, CoinbaseBuy=${coinbaseBuyVolume}, CoinbaseSell=${coinbaseSellVolume}")
        }
        maxVol
    }
    
    // Track previous volumes to detect changes
    var prevBinanceBuy by remember { mutableStateOf<Double?>(null) }
    var prevBinanceSell by remember { mutableStateOf<Double?>(null) }
    var prevCoinbaseBuy by remember { mutableStateOf<Double?>(null) }
    var prevCoinbaseSell by remember { mutableStateOf<Double?>(null) }
    
    // Watch for Binance volume changes and trigger animation
    LaunchedEffect(binanceBuyVolume, binanceSellVolume) {
        val buyChanged = binanceBuyVolume != prevBinanceBuy
        val sellChanged = binanceSellVolume != prevBinanceSell
        
        if (buyChanged || sellChanged) {
            prevBinanceBuy = binanceBuyVolume
            prevBinanceSell = binanceSellVolume
            
            if (binanceBuyVolume != null || binanceSellVolume != null) {
                binanceVolumeAnimating = true
                delay(500)
                binanceVolumeAnimating = false
            }
        }
    }
    
    // Watch for Coinbase volume changes and trigger animation
    LaunchedEffect(coinbaseBuyVolume, coinbaseSellVolume) {
        val buyChanged = coinbaseBuyVolume != prevCoinbaseBuy
        val sellChanged = coinbaseSellVolume != prevCoinbaseSell
        
        if (buyChanged || sellChanged) {
            prevCoinbaseBuy = coinbaseBuyVolume
            prevCoinbaseSell = coinbaseSellVolume
            
            if (coinbaseBuyVolume != null || coinbaseSellVolume != null) {
                coinbaseVolumeAnimating = true
                delay(500)
                coinbaseVolumeAnimating = false
            }
        }
    }
    
    // Update max volume when any volume changes
    LaunchedEffect(binanceBuyVolume, binanceSellVolume, coinbaseBuyVolume, coinbaseSellVolume) {
        maxVolume = calculateMaxVolume()
    }
    
    // Update max Binance BUY volume (track historical maximum)
    LaunchedEffect(binanceBuyVolume) {
        val currentBuy = binanceBuyVolume ?: 0.0
        if (currentBuy > 0.0 && (maxBinanceBuyVolume == 0.0 || currentBuy > maxBinanceBuyVolume)) {
            maxBinanceBuyVolume = currentBuy
            if (ENABLE_PUNCH_LOGS) {
                Log.d("PunchDebug", "New max Binance BUY Volume: $maxBinanceBuyVolume (current: $currentBuy)")
            }
        }
    }
    
    // Update max Coinbase BUY volume (track historical maximum)
    LaunchedEffect(coinbaseBuyVolume) {
        val currentBuy = coinbaseBuyVolume ?: 0.0
        if (currentBuy > 0.0 && (maxCoinbaseBuyVolume == 0.0 || currentBuy > maxCoinbaseBuyVolume)) {
            maxCoinbaseBuyVolume = currentBuy
            if (ENABLE_PUNCH_LOGS) {
                Log.d("PunchDebug", "New max Coinbase BUY Volume: $maxCoinbaseBuyVolume (current: $currentBuy)")
            }
        }
    }
    
    // Update max Binance SELL volume (track historical maximum for future villain animations)
    LaunchedEffect(binanceSellVolume) {
        val currentSell = binanceSellVolume ?: 0.0
        if (currentSell > 0.0 && (maxBinanceSellVolume == 0.0 || currentSell > maxBinanceSellVolume)) {
            maxBinanceSellVolume = currentSell
            if (ENABLE_PUNCH_LOGS) {
                Log.d("PunchDebug", "New max Binance SELL Volume: $maxBinanceSellVolume (current: $currentSell)")
            }
        }
    }
    
    // Update max Coinbase SELL volume (track historical maximum for future villain animations)
    LaunchedEffect(coinbaseSellVolume) {
        val currentSell = coinbaseSellVolume ?: 0.0
        if (currentSell > 0.0 && (maxCoinbaseSellVolume == 0.0 || currentSell > maxCoinbaseSellVolume)) {
            maxCoinbaseSellVolume = currentSell
            if (ENABLE_PUNCH_LOGS) {
                Log.d("PunchDebug", "New max Coinbase SELL Volume: $maxCoinbaseSellVolume (current: $currentSell)")
            }
        }
    }
    
    // Connect WebSocket on composition
    LaunchedEffect(Unit) {
        if (useWebSocket) {
            webSocketRepository.connect()
        }
    }
    
    // Elapsed timer: tick every second so HH:MM:SS recomputes; update overTenMin so flash effect is not cancelled every second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
            val elapsedMs = lastBlockHeightUpdateTimeMs?.let { System.currentTimeMillis() - it } ?: 0L
            overTenMin = elapsedMs >= TIMER_FLASH_WHEN_ELAPSED_MS
        }
    }
    
    // When elapsed >= 10 min, flash timer 3 times every 30 seconds (keyed on overTenMin so effect is not cancelled every tick)
    LaunchedEffect(overTenMin, lastBlockHeightUpdateTimeMs) {
        if (!overTenMin) {
            timerOverTenMinFlashOn = false
            return@LaunchedEffect
        }
        while (true) {
            val elapsedMs = lastBlockHeightUpdateTimeMs?.let { System.currentTimeMillis() - it } ?: 0L
            if (elapsedMs < TIMER_FLASH_WHEN_ELAPSED_MS) {
                timerOverTenMinFlashOn = false
                return@LaunchedEffect
            }
            delay(TIMER_FLASH_INTERVAL_MS)
            repeat(3) {
                timerOverTenMinFlashOn = true
                delay(BLOCK_HEIGHT_FLASH_HALF_MS)
                timerOverTenMinFlashOn = false
                delay(BLOCK_HEIGHT_FLASH_HALF_MS)
            }
        }
    }
    
    // Block height update flash: 10 times when value changes (non-null)
    LaunchedEffect(blockHeight) {
        if (blockHeight == null) return@LaunchedEffect
        repeat(10) {
            blockHeightFlashOn = true
            delay(BLOCK_HEIGHT_FLASH_HALF_MS)
            blockHeightFlashOn = false
            delay(BLOCK_HEIGHT_FLASH_HALF_MS)
        }
    }
    
    // Update state from WebSocket data
    LaunchedEffect(binanceWebSocketData) {
        if (useWebSocket && binanceWebSocketData.isConnected) {
            binanceWebSocketData.price?.let { price ->
                binancePreviousPrice = binancePrice
                binancePrice = price
            }
            binanceIsConnected = binanceWebSocketData.isConnected
            binanceBuyVolume = binanceWebSocketData.buyVolume
            binanceSellVolume = binanceWebSocketData.sellVolume
        }
    }
    
    LaunchedEffect(coinbaseWebSocketData) {
        if (useWebSocket && coinbaseWebSocketData.isConnected) {
            coinbaseWebSocketData.price?.let { price ->
                coinbasePreviousPrice = coinbasePrice
                coinbasePrice = price
            }
            coinbaseIsConnected = coinbaseWebSocketData.isConnected
            coinbaseBuyVolume = coinbaseWebSocketData.buyVolume
            coinbaseSellVolume = coinbaseWebSocketData.sellVolume
        }
    }
    
    // Poll APIs every 5 seconds as fallback (only if WebSocket is disabled or not connected)
    LaunchedEffect(useWebSocket, binanceWebSocketData.isConnected, coinbaseWebSocketData.isConnected) {
        if (!useWebSocket || (!binanceWebSocketData.isConnected && !coinbaseWebSocketData.isConnected)) {
            while (true) {
                // Fetch Binance price
                repository.getBinancePrice().fold(
                    onSuccess = { price ->
                        binancePreviousPrice = binancePrice
                        binancePrice = price
                        binanceIsConnected = true
                    },
                    onFailure = {
                        binanceIsConnected = false
                    }
                )
                
                // Fetch Coinbase price
                repository.getCoinbasePrice().fold(
                    onSuccess = { price ->
                        coinbasePreviousPrice = coinbasePrice
                        coinbasePrice = price
                        coinbaseIsConnected = true
                    },
                    onFailure = {
                        coinbaseIsConnected = false
                    }
                )
                
                // Fetch Binance volumes
                repository.getBinanceVolumes().fold(
                    onSuccess = { (buy, sell) ->
                        val oldBuy = binanceBuyVolume
                        val oldSell = binanceSellVolume
                        binanceBuyVolume = buy
                        binanceSellVolume = sell
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.d("MainActivity", "Binance volumes updated - Buy: $buy (was $oldBuy), Sell: $sell (was $oldSell)")
                        }
                    },
                    onFailure = { e ->
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.e("MainActivity", "Failed to fetch Binance volumes: ${e.message}", e)
                        }
                        binanceBuyVolume = null
                        binanceSellVolume = null
                    }
                )
                
                // Fetch Coinbase volumes
                repository.getCoinbaseVolumes().fold(
                    onSuccess = { (buy, sell) ->
                        val oldBuy = coinbaseBuyVolume
                        val oldSell = coinbaseSellVolume
                        coinbaseBuyVolume = buy
                        coinbaseSellVolume = sell
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.d("MainActivity", "Coinbase volumes updated - Buy: $buy (was $oldBuy), Sell: $sell (was $oldSell)")
                        }
                    },
                    onFailure = { e ->
                        if (ENABLE_EXCHANGE_LOGS) {
                            Log.e("MainActivity", "Failed to fetch Coinbase volumes: ${e.message}", e)
                        }
                        coinbaseBuyVolume = null
                        coinbaseSellVolume = null
                    }
                )
                
                delay(5000) // 5 seconds
            }
        }
    }
    
    // Manage all sprites in a list
    val sprites = remember { mutableStateListOf<SpriteData>() }
    
    // Track punch state for Satoshi
    var currentLeftPunch by remember { mutableStateOf<PunchType?>(null) }
    var currentRightPunch by remember { mutableStateOf<PunchType?>(null) }
    
    // Queue for pending punches (lower priority)
    // Track last punch time per punch type for cooldown management
    var lastPunchTime by remember { 
        mutableStateOf<Map<PunchType, Long>>(emptyMap()) 
    }
    
    // Track current defense type and switch time for defense cooldown
    var lastDefenseType by remember { mutableStateOf<DefenseType?>(null) }
    var lastDefenseSwitchTime by remember { mutableStateOf(0L) }
    var lastDefenseCompletionTime by remember { mutableStateOf(0L) }
    
    // Track punch/defense state for Lizard (villain - uses SELL volume)
    var currentLizardLeftPunch by remember { mutableStateOf<PunchType?>(null) }
    var currentLizardRightPunch by remember { mutableStateOf<PunchType?>(null) }
    var lastLizardPunchTime by remember { mutableStateOf<Map<PunchType, Long>>(emptyMap()) }
    var lastLizardDefenseType by remember { mutableStateOf<DefenseType?>(null) }
    var lastLizardDefenseSwitchTime by remember { mutableStateOf(0L) }
    var lastLizardDefenseCompletionTime by remember { mutableStateOf(0L) }
    
    // Damage system: boxer in damage = show damage animation (skip for now), pause both bobbing
    var satoshiInDamage by remember { mutableStateOf(false) }
    var satoshiDamageType by remember { mutableStateOf<DamageAnimationType?>(null) }
    var lizardInDamage by remember { mutableStateOf(false) }
    var lizardDamageType by remember { mutableStateOf<DamageAnimationType?>(null) }
    // Damage bar 0-100; KO sequence when >= 100
    var satoshiDamagePoints by remember { mutableStateOf(0) }
    var lizardDamagePoints by remember { mutableStateOf(0) }
    var satoshiKOPhase by remember { mutableStateOf<KOPhase?>(null) }
    var lizardKOPhase by remember { mutableStateOf<KOPhase?>(null) }

    // Bitcoin block height: poll Mempool.space once per minute; reset elapsed timer only when value changes
    LaunchedEffect(Unit) {
        while (true) {
            repository.getBlockTipHeight().onSuccess { newHeight ->
                val valueChanged = (blockHeight != newHeight)
                blockHeight = newHeight
                if (valueChanged) {
                    lastBlockHeightUpdateTimeMs = System.currentTimeMillis()
                    val noKo = satoshiKOPhase == null && lizardKOPhase == null
                    val notRotating = !isRingRotating
                    if (!TEST_RING_ROTATION && noKo && notRotating) ringRotationTriggerCount++
                    if (!SPAWN_CAT && !sprites.any { it.spriteType == SpriteType.CAT }) catSpawnTriggerCount++
                }
            }
            delay(BLOCK_HEIGHT_REFRESH_INTERVAL_MS)
        }
    }

    // Test mode: trigger ring rotation on a timer instead of block height
    LaunchedEffect(TEST_RING_ROTATION) {
        if (!TEST_RING_ROTATION) return@LaunchedEffect
        while (true) {
            delay(RING_ROTATE_FREQUENCY_MS)
            val noKo = satoshiKOPhase == null && lizardKOPhase == null
            val notRotating = !isRingRotating
            if (noKo && notRotating) ringRotationTriggerCount++
        }
    }

    // Cat spawn on timer when SPAWN_CAT true
    LaunchedEffect(SPAWN_CAT) {
        if (!SPAWN_CAT) return@LaunchedEffect
        while (true) {
            delay(CAT_SPAWN_INTERVAL_MS)
            if (!sprites.any { it.spriteType == SpriteType.CAT }) catSpawnTriggerCount++
        }
    }

    // Run one ring rotation when trigger fires (block height update or test timer)
    LaunchedEffect(ringRotationTriggerCount) {
        if (ringRotationTriggerCount == 0 || isRingRotating || RING_FRAMES.size < 2) return@LaunchedEffect
        isRingRotating = true
        try {
            val right = when (RING_ROTATE_DIRECTION) {
                RingRotateDirection.Random -> kotlin.random.Random.nextBoolean()
                RingRotateDirection.Right -> true
                RingRotateDirection.Left -> false
            }
            val lastRotationFrame = RING_FRAMES.size - 1
            val sequence = if (right) (1..lastRotationFrame).toList() else (lastRotationFrame downTo 1).toList()
            for (frame in sequence) {
                backgroundFrameIndices = listOf(
                    frame,
                    backgroundFrameIndices.getOrElse(1) { 0 },
                    backgroundFrameIndices.getOrElse(2) { 0 }
                )
                var elapsed = 0L
                while (elapsed < RING_ROTATION_FRAME_DELAY_MS) {
                    delay(50)
                    if (satoshiKOPhase == null && lizardKOPhase == null) elapsed += 50
                }
            }
            backgroundFrameIndices = listOf(
                0,
                backgroundFrameIndices.getOrElse(1) { 0 },
                backgroundFrameIndices.getOrElse(2) { 0 }
            )
        } finally {
            isRingRotating = false
        }
    }

    // Audience (bg3): loop frames 0 -> 1 -> 2 -> 0 within current ring set at AUDIENCE_FRAME_DELAY_MS
    LaunchedEffect(AUDIENCE_FRAMES_BY_RING.isNotEmpty()) {
        if (AUDIENCE_FRAMES_BY_RING.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(AUDIENCE_FRAME_DELAY_MS)
            val next = (backgroundFrameIndices.getOrElse(2) { 0 } + 1) % 3
            backgroundFrameIndices = listOf(
                backgroundFrameIndices.getOrElse(0) { 0 },
                backgroundFrameIndices.getOrElse(1) { 0 },
                next
            )
        }
    }

    // bg2 signs: clear all when ring frame index changes
    LaunchedEffect(backgroundFrameIndices) {
        val ring = backgroundFrameIndices.getOrElse(0) { 0 }
        if (ring != lastRingFrameIndex) {
            signSpawns = emptyList()
            lastRingFrameIndex = ring
        }
    }

    // Pending impact check: run hit detection once at 2nd-last frame
    var pendingSatoshiImpactCheck by remember { mutableStateOf<ImpactCheckData?>(null) }
    var pendingLizardImpactCheck by remember { mutableStateOf<ImpactCheckData?>(null) }
    // Deferred damage when hit during wrong block (defense plays to completion, then damage applied)
    var pendingSatoshiDamageAfterDefense by remember { mutableStateOf<DamageAnimationType?>(null) }
    var pendingLizardDamageAfterDefense by remember { mutableStateOf<DamageAnimationType?>(null) }
    var pendingSatoshiDamagePunchType by remember { mutableStateOf<PunchType?>(null) }
    var pendingLizardDamagePunchType by remember { mutableStateOf<PunchType?>(null) }
    // Bobbing movement (boxers move left/right gradually, in opposite directions)
    var movementOffsetX by remember { mutableStateOf(0f) }
    var movementDirection by remember { mutableStateOf(1) }  // 1 = right, -1 = left
    // Y bobbing (both move up/down together - simulates engaging/disengaging)
    var movementOffsetY by remember { mutableStateOf(0f) }
    var movementDirectionY by remember { mutableStateOf(1) }  // 1 = down, -1 = up
    var applyYOnNextCentreCross by remember { mutableStateOf(false) }  // flip on each centre cross; apply Y when false after flip (every second cross)
    
    LaunchedEffect(satoshiInDamage, lizardInDamage, satoshiKOPhase, lizardKOPhase) {
        while (true) {
            delay(BOBBING_INTERVAL_MS)
            // Pause bobbing when either boxer is in damage or KO sequence
            if (satoshiInDamage || lizardInDamage || satoshiKOPhase != null || lizardKOPhase != null) continue
            val oldOffsetX = movementOffsetX
            movementOffsetX += movementDirection * BOBBING_STEP_PX
            when {
                movementOffsetX >= BOBBING_MAX_X_RIGHT -> {
                    movementOffsetX = BOBBING_MAX_X_RIGHT
                    movementDirection = -1
                }
                movementOffsetX <= BOBBING_MAX_X_LEFT -> {
                    movementOffsetX = BOBBING_MAX_X_LEFT
                    movementDirection = 1
                }
            }
            val centreCrossed = (oldOffsetX > 0 && movementOffsetX < 0) || (oldOffsetX < 0 && movementOffsetX > 0)
            if (centreCrossed) {
                applyYOnNextCentreCross = !applyYOnNextCentreCross
                if (!applyYOnNextCentreCross) {
                    repeat(BOBBING_Y_STEPS_PER_FULL_X_CYCLE) {
                        movementOffsetY += movementDirectionY * BOBBING_STEP_PX
                        when {
                            movementOffsetY >= BOBBING_MAX_Y_DOWN -> {
                                movementOffsetY = BOBBING_MAX_Y_DOWN
                                movementDirectionY = -1
                            }
                            movementOffsetY <= BOBBING_MAX_Y_UP -> {
                                movementOffsetY = BOBBING_MAX_Y_UP
                                movementDirectionY = 1
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Determine punch types from BUY volumes
    LaunchedEffect(ENABLE_PUNCHING, binanceBuyVolume, coinbaseBuyVolume, maxBinanceBuyVolume, maxCoinbaseBuyVolume) {
        if (ENABLE_PUNCH_LOGS) {
            Log.d("PunchDebug", "=== PUNCH STATE UPDATE LaunchedEffect RUNNING ===")
            Log.d("PunchDebug", "Inputs - Binance BUY: $binanceBuyVolume (max: $maxBinanceBuyVolume), Coinbase BUY: $coinbaseBuyVolume (max: $maxCoinbaseBuyVolume)")
            Log.d("PunchDebug", "Current State - Left: $currentLeftPunch, Right: $currentRightPunch")
        }
        
        if (!ENABLE_PUNCHING) {
            currentLeftPunch = null
            currentRightPunch = null
        } else {
            // Update left hand punch (Binance BUY volume) - uses Binance's own max
            val newLeftPunch = getPunchTypeFromVolume(binanceBuyVolume, maxBinanceBuyVolume)
            if (ENABLE_PUNCH_LOGS) {
                Log.d("PunchDebug", "Left punch determined: $newLeftPunch (was: $currentLeftPunch)")
            }
            if (ENABLE_PUNCH_LOGS && newLeftPunch != currentLeftPunch) {
                Log.d("PunchDebug", "LEFT PUNCH CHANGED - Binance BUY: ${binanceBuyVolume}, maxBinanceBuyVolume: $maxBinanceBuyVolume, old: $currentLeftPunch, new: $newLeftPunch")
            }
            currentLeftPunch = newLeftPunch
            
            // Update right hand punch (Coinbase BUY volume) - uses Coinbase's own max
            val newRightPunch = getPunchTypeFromVolume(coinbaseBuyVolume, maxCoinbaseBuyVolume)
            if (ENABLE_PUNCH_LOGS) {
                Log.d("PunchDebug", "Right punch determined: $newRightPunch (was: $currentRightPunch)")
            }
            if (ENABLE_PUNCH_LOGS && newRightPunch != currentRightPunch) {
                Log.d("PunchDebug", "RIGHT PUNCH CHANGED - Coinbase BUY: ${coinbaseBuyVolume}, maxCoinbaseBuyVolume: $maxCoinbaseBuyVolume, old: $currentRightPunch, new: $newRightPunch")
            }
            currentRightPunch = newRightPunch
        }
        
        if (ENABLE_PUNCH_LOGS) {
            Log.d("PunchDebug", "=== PUNCH STATE UPDATE COMPLETE - Left: $currentLeftPunch, Right: $currentRightPunch ===")
        }
    }
    
    // Determine Lizard punch types from SELL volumes (villain mirrors hero)
    LaunchedEffect(ENABLE_PUNCHING, binanceSellVolume, coinbaseSellVolume, maxBinanceSellVolume, maxCoinbaseSellVolume) {
        if (!ENABLE_PUNCHING) {
            currentLizardLeftPunch = null
            currentLizardRightPunch = null
        } else {
            currentLizardLeftPunch = getPunchTypeFromVolume(binanceSellVolume, maxBinanceSellVolume)
            currentLizardRightPunch = getPunchTypeFromVolume(coinbaseSellVolume, maxCoinbaseSellVolume)
        }
    }
    
    // Update Satoshi sprite based on punch state
    LaunchedEffect(currentLeftPunch, currentRightPunch, sprites, binanceBuyVolume, coinbaseBuyVolume, maxBinanceBuyVolume, maxCoinbaseBuyVolume, lastPunchTime, isBinanceDefense, lastDefenseType, lastDefenseSwitchTime, lastDefenseCompletionTime, satoshiInDamage, satoshiKOPhase, lizardKOPhase) {
        // Block punch/defense updates while in damage or KO sequence
        if (satoshiKOPhase != null) {
            // #region agent log
            agentLog("MainActivity:SatoshiSpriteUpdate", "early_return_ko", "{\"satoshiKOPhase\":\"$satoshiKOPhase\"}", "H1")
            // #endregion
            return@LaunchedEffect
        }
        // Opponent (Lizard) in KO - Satoshi stays idle, no punching
        if (lizardKOPhase != null) {
            val s = sprites.find { it.spriteType == SpriteType.SATOSHI }
            if (s != null && (s.currentPunchType != null || s.isPunching || s.animationFrames != SATOSHI_IDLE_FRAMES)) {
                val idx = sprites.indexOf(s)
                sprites[idx] = s.copy(
                    animationFrames = SATOSHI_IDLE_FRAMES, currentFrameIndex = 0, isAnimated = true,
                    currentPunchType = null, currentHandSide = null, isPunching = false,
                    currentDefenseType = null, isDefending = false, sizeScale = SATOSHI_SCALE
                )
            }
            return@LaunchedEffect
        }
        if (satoshiInDamage) {
            if (DAMAGE_DEBUG) {
                Log.d("DamageDebug", "{\"h\":\"D\",\"loc\":\"satoshi_sprite_update_early_return\",\"ts\":${System.currentTimeMillis()}}")
            }
            return@LaunchedEffect
        }
        // Clear defense cooldown state when leaving defense mode or when dodging disabled
        if (!isBinanceDefense || !ENABLE_DODGING) {
            lastDefenseType = null
            lastDefenseSwitchTime = 0L
            lastDefenseCompletionTime = 0L
        }
        
        if (ENABLE_PUNCH_LOGS) {
            Log.d("PunchDebug", "=== SPRITE UPDATE LaunchedEffect RUNNING ===")
            Log.d("PunchDebug", "Punch State - Left: $currentLeftPunch, Right: $currentRightPunch")
            Log.d("PunchDebug", "Volumes - Binance: $binanceBuyVolume, Coinbase: $coinbaseBuyVolume")
        }
        
        val satoshiSprite = sprites.find { it.spriteType == SpriteType.SATOSHI }
        if (satoshiSprite == null) {
            if (ENABLE_PUNCH_LOGS) {
                Log.w("PunchDebug", "Satoshi sprite not found in sprites list (size: ${sprites.size})")
            }
            return@LaunchedEffect
        }
        
        val index = sprites.indexOf(satoshiSprite)

        // If currently defending, wait for completion (return early)
        if (satoshiSprite.isDefending) {
            if (ENABLE_BLOCKING_LOGS) {
                Log.d("BlockingDebug", "Currently defending - waiting for completion")
            }
            return@LaunchedEffect
        }

        // If Binance defense is active and dodging enabled, use defense animations (with cooldown)
        // Require minimum idle after defense completes before re-entering defense
        if (ENABLE_DODGING && isBinanceDefense &&
            (lastDefenseCompletionTime == 0L || (System.currentTimeMillis() - lastDefenseCompletionTime) >= MIN_IDLE_AFTER_DEFENSE_MS)) {
            val newDefenseType = getDefenseTypeFromVolume(
                binanceBuyVolume,
                coinbaseBuyVolume,
                maxBinanceBuyVolume,
                maxCoinbaseBuyVolume
            )
            
            val onCooldown = lastDefenseType != null &&
                    newDefenseType != lastDefenseType &&
                    isDefenseOnCooldown(lastDefenseType, lastDefenseSwitchTime)
            
            val effectiveType = if (onCooldown) lastDefenseType else newDefenseType
            
            if (ENABLE_BLOCKING_LOGS && onCooldown) {
                Log.d("BlockingDebug", "Defense cooldown - keeping $lastDefenseType, rejected switch to $newDefenseType")
            }
            
            val defenseFrames = when (effectiveType) {
                DefenseType.HEAD_BLOCK -> SATOSHI_HEAD_BLOCK_FRAMES
                DefenseType.BODY_BLOCK -> SATOSHI_BODY_BLOCK_FRAMES
                DefenseType.DODGE_LEFT -> SATOSHI_DODGE_LEFT_FRAMES
                DefenseType.DODGE_RIGHT -> SATOSHI_DODGE_RIGHT_FRAMES
                null -> emptyList() // No defense frames when there is no defense type
            }
            
            // If this defense type has no frames, fall back to idle only when not defending and not right after defense started (avoids flicker)
            if (defenseFrames.isEmpty()) {
                if (satoshiSprite.isDefending) {
                    if (ENABLE_BLOCKING_LOGS) {
                        Log.d("BlockingDebug", "Defense type null but currently defending - waiting for completion to clear")
                    }
                    return@LaunchedEffect
                }
                val timeSinceDefenseSwitch = System.currentTimeMillis() - lastDefenseSwitchTime
                if (lastDefenseType != null && timeSinceDefenseSwitch < MIN_IDLE_AFTER_DEFENSE_MS) {
                    if (ENABLE_BLOCKING_LOGS) {
                        Log.d("BlockingDebug", "Defense type null but recently in defense (${timeSinceDefenseSwitch}ms) - skip clear to avoid flicker")
                    }
                    return@LaunchedEffect
                }
                // Already idle: don't reset sprite (avoids idle animation restart flicker)
                if (satoshiSprite.animationFrames == SATOSHI_IDLE_FRAMES && satoshiSprite.currentDefenseType == null && !satoshiSprite.isDefending) {
                    return@LaunchedEffect
                }
                if (ENABLE_BLOCKING_LOGS) {
                    Log.w("BlockingDebug", "Defense type $effectiveType has no frames - falling back to idle")
                }
                if (ENABLE_BLOCKING_LOGS) Log.d("DefenseClear", "{\"source\":\"empty_frames\",\"ts\":${System.currentTimeMillis()}}")
                sprites[index] = satoshiSprite.copy(
                    animationFrames = SATOSHI_IDLE_FRAMES,
                    currentFrameIndex = 0,
                    isAnimated = true,
                    currentPunchType = null,
                    currentHandSide = null,
                    isPunching = false,
                    playAnimationOnce = false,
                    currentDefenseType = null,
                    isDefending = false,
                    sizeScale = SATOSHI_SCALE
                )
                return@LaunchedEffect
            }
            
            // Only set isDefending = true when starting a NEW defense animation (defense type changed)
            val isNewDefense = satoshiSprite.currentDefenseType != effectiveType
            
            if (ENABLE_BLOCKING_LOGS && !onCooldown) {
                val binancePct = binanceBuyVolume?.let { b -> if (maxBinanceBuyVolume > 0.0) "%.2f".format((b / maxBinanceBuyVolume) * 100) else "N/A" } ?: "N/A"
                val coinbasePct = coinbaseBuyVolume?.let { c -> if (maxCoinbaseBuyVolume > 0.0) "%.2f".format((c / maxCoinbaseBuyVolume) * 100) else "N/A" } ?: "N/A"
                Log.d("BlockingDebug", "Defense sequence activated - type: $effectiveType, Binance BUY %: $binancePct%, Coinbase BUY %: $coinbasePct%, frames: ${defenseFrames.size}, isNewDefense: $isNewDefense")
            }
            
            sprites[index] = satoshiSprite.copy(
                animationFrames = defenseFrames,
                currentFrameIndex = 0,
                isAnimated = defenseFrames.isNotEmpty(),
                currentPunchType = null,
                currentHandSide = null,
                isPunching = false,
                playAnimationOnce = true,
                currentDefenseType = effectiveType,
                isDefending = if (isNewDefense) true else satoshiSprite.isDefending,
                sizeScale = SATOSHI_SCALE
            )
            
            if (!onCooldown) {
                lastDefenseType = effectiveType
                lastDefenseSwitchTime = System.currentTimeMillis()
            }
            return@LaunchedEffect
        }
        
        // If currently punching, wait for completion (return early)
        if (satoshiSprite.isPunching) {
            if (ENABLE_PUNCH_LOGS) {
                Log.d("PunchDebug", "Currently punching - waiting for completion")
            }
            return@LaunchedEffect
        }
        
        // If animation completed but sprite still has punch state, clear it
        if (!satoshiSprite.isPunching && satoshiSprite.currentPunchType != null) {
            if (ENABLE_PUNCH_LOGS) {
                Log.d("PunchDebug", "Animation completed - clearing punch state and transitioning to idle")
            }
            if (ENABLE_BLOCKING_LOGS) Log.d("DefenseClear", "{\"source\":\"punch_state_clear\",\"ts\":${System.currentTimeMillis()}}")
            sprites[index] = satoshiSprite.copy(
                animationFrames = SATOSHI_IDLE_FRAMES,
                currentFrameIndex = 0,
                currentPunchType = null,
                currentHandSide = null,
                isPunching = false,
                currentDefenseType = null,
                isDefending = false,
                sizeScale = SATOSHI_SCALE
            )
            return@LaunchedEffect
        }
        
        // Check idle conditions first
        val shouldPlayIdle = (currentLeftPunch == null && currentRightPunch == null) || 
                             areAllPunchesOnCooldown(lastPunchTime)
        
        if (shouldPlayIdle) {
            if (ENABLE_PUNCH_LOGS) {
                Log.d("PunchDebug", "Playing idle animation - no punches or all on cooldown")
            }
            // Update sprite to idle animation
            if (satoshiSprite.currentPunchType != null || satoshiSprite.isPunching || 
                satoshiSprite.animationFrames != SATOSHI_IDLE_FRAMES) {
                if (satoshiSprite.isDefending && ENABLE_BLOCKING_LOGS) Log.d("DefenseClear", "{\"source\":\"idle\",\"ts\":${System.currentTimeMillis()}}")
                sprites[index] = satoshiSprite.copy(
                    animationFrames = SATOSHI_IDLE_FRAMES,
                    currentFrameIndex = 0,
                    isAnimated = true,
                    currentPunchType = null,
                    currentHandSide = null,
                    isPunching = false,
                    currentDefenseType = null,
                    isDefending = false,
                    sizeScale = SATOSHI_SCALE
                )
            }
            return@LaunchedEffect
        }

        // Determine which punch to execute based on priority
        val punchToExecute = when {
            currentLeftPunch != null && currentRightPunch != null -> {
                // Both active - use priority hand
                if (PUNCH_PRIORITY_HAND == HandSide.LEFT) {
                    Pair(HandSide.LEFT, currentLeftPunch!!)
                } else {
                    Pair(HandSide.RIGHT, currentRightPunch!!)
                }
            }
            currentLeftPunch != null -> Pair(HandSide.LEFT, currentLeftPunch!!)
            currentRightPunch != null -> Pair(HandSide.RIGHT, currentRightPunch!!)
            else -> null
        }
        
        // Check cooldown if punch determined
        if (punchToExecute != null) {
            val (handSide, punchType) = punchToExecute
            if (isPunchOnCooldown(punchType, lastPunchTime)) {
                if (ENABLE_PUNCH_LOGS) {
                    Log.d("PunchDebug", "Punch on cooldown - Type: $punchType")
                }
                // On cooldown - check if all punches are on cooldown
                if (areAllPunchesOnCooldown(lastPunchTime)) {
                    if (ENABLE_PUNCH_LOGS) {
                        Log.d("PunchDebug", "All punches on cooldown - playing idle")
                    }
                    if (satoshiSprite.isDefending && ENABLE_BLOCKING_LOGS) Log.d("DefenseClear", "{\"source\":\"cooldown_idle\",\"ts\":${System.currentTimeMillis()}}")
                    // All on cooldown - play idle
                    sprites[index] = satoshiSprite.copy(
                        animationFrames = SATOSHI_IDLE_FRAMES,
                        currentFrameIndex = 0,
                        isAnimated = true,
                        currentPunchType = null,
                        currentHandSide = null,
                        isPunching = false,
                        currentDefenseType = null,
                        isDefending = false,
                        sizeScale = SATOSHI_SCALE
                    )
                }
                return@LaunchedEffect
            }

            // Execute punch only if not defending (guard against race where we passed the early return)
            if (satoshiSprite.isDefending) {
                if (ENABLE_BLOCKING_LOGS) {
                    Log.d("BlockingDebug", "Skipping punch - sprite is defending (guard)")
                }
                return@LaunchedEffect
            }
            val frames = getPunchFrames(handSide, punchType)
            if (frames.isNotEmpty()) {
                if (ENABLE_PUNCH_LOGS) {
                    Log.d("PunchDebug", "EXECUTING PUNCH - Hand: $handSide, Type: $punchType")
                }
                // Update sprite with punch animation
                sprites[index] = satoshiSprite.copy(
                    animationFrames = frames,
                    currentFrameIndex = 0,
                    isAnimated = true,
                    currentPunchType = punchType,
                    currentHandSide = handSide,
                    isPunching = true,
                    currentDefenseType = null,
                    isDefending = false,
                    sizeScale = SATOSHI_SCALE
                )
                // Schedule hit detection at 2nd-last frame
                pendingSatoshiImpactCheck = ImpactCheckData(punchType, handSide, frames.size)
                // Update lastPunchTime map with current time for this punch type
                lastPunchTime = lastPunchTime + (punchType to System.currentTimeMillis())
            } else {
                if (ENABLE_PUNCH_LOGS) {
                    Log.w("PunchDebug", "PUNCH FRAMES EMPTY - Hand: $handSide, Type: $punchType")
                }
            }
        } else {
            // No punch - return to idle (shouldn't reach here due to shouldPlayIdle check)
            if (ENABLE_PUNCH_LOGS) {
                Log.d("PunchDebug", "No punch determined - returning to idle")
            }
            sprites[index] = satoshiSprite.copy(
                animationFrames = SATOSHI_IDLE_FRAMES,
                currentFrameIndex = 0,
                isAnimated = true,
                currentPunchType = null,
                currentHandSide = null,
                isPunching = false,
                sizeScale = SATOSHI_SCALE
            )
        }
        
        if (ENABLE_PUNCH_LOGS) {
            Log.d("PunchDebug", "=== SPRITE UPDATE COMPLETE ===")
        }
    }
    
    // Update Lizard sprite (villain - uses SELL volume, offense when sell > buy)
    val isLizardDefense = !isBinanceDefense  // Lizard defends when hero attacks
    LaunchedEffect(currentLizardLeftPunch, currentLizardRightPunch, sprites, binanceSellVolume, coinbaseSellVolume, maxBinanceSellVolume, maxCoinbaseSellVolume, lastLizardPunchTime, isLizardDefense, lastLizardDefenseType, lastLizardDefenseSwitchTime, lastLizardDefenseCompletionTime, lizardInDamage, lizardKOPhase, satoshiKOPhase) {
        // #region agent log
        agentLog("MainActivity:LizardSpriteUpdate", "entry", "{\"lizardInDamage\":$lizardInDamage,\"isLizardDefense\":$isLizardDefense}", "A")
        // #endregion
        if (lizardKOPhase != null) {
            // #region agent log
            agentLog("MainActivity:LizardSpriteUpdate", "early_return_ko", "{\"lizardKOPhase\":\"$lizardKOPhase\"}", "H2")
            // #endregion
            return@LaunchedEffect
        }
        // Opponent (Satoshi) in KO - Lizard stays idle, no punching
        if (satoshiKOPhase != null) {
            val l = sprites.find { it.spriteType == SpriteType.LIZARD }
            if (l != null && (l.currentPunchType != null || l.isPunching || l.animationFrames != LIZARD_IDLE_FRAMES)) {
                val idx = sprites.indexOf(l)
                sprites[idx] = l.copy(
                    animationFrames = LIZARD_IDLE_FRAMES, currentFrameIndex = 0, isAnimated = true,
                    currentPunchType = null, currentHandSide = null, isPunching = false,
                    currentDefenseType = null, isDefending = false, playAnimationOnce = false,
                    sizeScale = LIZARD_SCALE
                )
            }
            return@LaunchedEffect
        }
        if (lizardInDamage) {
            // #region agent log
            agentLog("MainActivity:LizardSpriteUpdate", "early_return", "{\"reason\":\"lizardInDamage\"}", "B")
            // #endregion
            return@LaunchedEffect
        }
        if (isBinanceDefense || !ENABLE_DODGING) {
            lastLizardDefenseType = null
            lastLizardDefenseSwitchTime = 0L
            lastLizardDefenseCompletionTime = 0L
        }
        
        val lizardSprite = sprites.find { it.spriteType == SpriteType.LIZARD } ?: return@LaunchedEffect
        val index = sprites.indexOf(lizardSprite)
        // #region agent log
        agentLog("MainActivity:LizardSpriteUpdate", "state", "{\"isDefending\":${lizardSprite.isDefending},\"isPunching\":${lizardSprite.isPunching},\"playAnimationOnce\":${lizardSprite.playAnimationOnce},\"framesSize\":${lizardSprite.animationFrames.size}}", "A")
        // #endregion
        if (lizardSprite.isDefending) {
            // #region agent log
            agentLog("MainActivity:LizardSpriteUpdate", "early_return", "{\"reason\":\"isDefending\"}", "D")
            // #endregion
            return@LaunchedEffect
        }
        
        if (ENABLE_DODGING && isLizardDefense &&
            (lastLizardDefenseCompletionTime == 0L || (System.currentTimeMillis() - lastLizardDefenseCompletionTime) >= MIN_IDLE_AFTER_DEFENSE_MS)) {
            val newDefenseType = getDefenseTypeFromVolume(
                binanceSellVolume, coinbaseSellVolume,
                maxBinanceSellVolume, maxCoinbaseSellVolume
            )
            val onCooldown = lastLizardDefenseType != null && newDefenseType != lastLizardDefenseType &&
                isDefenseOnCooldown(lastLizardDefenseType, lastLizardDefenseSwitchTime)
            val effectiveType = if (onCooldown) lastLizardDefenseType else newDefenseType
            val defenseFrames = effectiveType?.let { getLizardDefenseFrames(it) } ?: emptyList()
            
            if (defenseFrames.isEmpty()) {
                // #region agent log
                agentLog("MainActivity:LizardSpriteUpdate", "set_idle", "{\"path\":\"defenseFramesEmpty\",\"playAnimationOnce\":false}", "A")
                // #endregion
                sprites[index] = lizardSprite.copy(
                    animationFrames = LIZARD_IDLE_FRAMES,
                    currentFrameIndex = 0, isAnimated = true,
                    currentPunchType = null, currentHandSide = null, isPunching = false,
                    playAnimationOnce = false, currentDefenseType = null, isDefending = false,
                    sizeScale = LIZARD_SCALE
                )
                return@LaunchedEffect
            }
            val isNewDefense = lizardSprite.currentDefenseType != effectiveType
            // #region agent log
            agentLog("MainActivity:LizardSpriteUpdate", "set_defense", "{\"effectiveType\":\"$effectiveType\"}", "C")
            // #endregion
            sprites[index] = lizardSprite.copy(
                animationFrames = defenseFrames,
                currentFrameIndex = 0, isAnimated = true,
                currentPunchType = null, currentHandSide = null, isPunching = false,
                playAnimationOnce = true, currentDefenseType = effectiveType,
                isDefending = if (isNewDefense) true else lizardSprite.isDefending,
                sizeScale = LIZARD_SCALE
            )
            if (!onCooldown) {
                lastLizardDefenseType = effectiveType
                lastLizardDefenseSwitchTime = System.currentTimeMillis()
            }
            return@LaunchedEffect
        }
        
        if (lizardSprite.isPunching) {
            // #region agent log
            agentLog("MainActivity:LizardSpriteUpdate", "early_return", "{\"reason\":\"isPunching\"}", "B")
            // #endregion
            return@LaunchedEffect
        }
        
        if (!lizardSprite.isPunching && lizardSprite.currentPunchType != null) {
            // #region agent log
            agentLog("MainActivity:LizardSpriteUpdate", "set_idle", "{\"path\":\"clear_punch_state\",\"hasPlayAnimationOnce\":false}", "C")
            // #endregion
            sprites[index] = lizardSprite.copy(
                animationFrames = LIZARD_IDLE_FRAMES,
                currentFrameIndex = 0, isAnimated = true,
                currentPunchType = null, currentHandSide = null,
                isPunching = false, currentDefenseType = null, isDefending = false,
                playAnimationOnce = false,
                sizeScale = LIZARD_SCALE
            )
            return@LaunchedEffect
        }
        
        val shouldPlayIdle = (currentLizardLeftPunch == null && currentLizardRightPunch == null) ||
            areAllPunchesOnCooldown(lastLizardPunchTime)
        if (shouldPlayIdle) {
            if (lizardSprite.currentPunchType != null || lizardSprite.isPunching ||
                lizardSprite.animationFrames != LIZARD_IDLE_FRAMES) {
                // #region agent log
                agentLog("MainActivity:LizardSpriteUpdate", "set_idle", "{\"path\":\"shouldPlayIdle\",\"hasPlayAnimationOnce\":false}", "A")
                // #endregion
                sprites[index] = lizardSprite.copy(
                    animationFrames = LIZARD_IDLE_FRAMES,
                    currentFrameIndex = 0, isAnimated = true,
                    currentPunchType = null, currentHandSide = null, isPunching = false,
                    currentDefenseType = null, isDefending = false,
                    playAnimationOnce = false,
                    sizeScale = LIZARD_SCALE
                )
            }
            return@LaunchedEffect
        }
        
        val punchToExecute = when {
            currentLizardLeftPunch != null && currentLizardRightPunch != null ->
                if (PUNCH_PRIORITY_HAND == HandSide.LEFT) Pair(HandSide.LEFT, currentLizardLeftPunch!!)
                else Pair(HandSide.RIGHT, currentLizardRightPunch!!)
            currentLizardLeftPunch != null -> Pair(HandSide.LEFT, currentLizardLeftPunch!!)
            currentLizardRightPunch != null -> Pair(HandSide.RIGHT, currentLizardRightPunch!!)
            else -> null
        }
        
        if (punchToExecute != null) {
            val (handSide, punchType) = punchToExecute
            if (isPunchOnCooldown(punchType, lastLizardPunchTime)) {
                if (areAllPunchesOnCooldown(lastLizardPunchTime)) {
                    // #region agent log
                    agentLog("MainActivity:LizardSpriteUpdate", "set_idle", "{\"path\":\"cooldown_idle\",\"hasPlayAnimationOnce\":false}", "A")
                    // #endregion
                    sprites[index] = lizardSprite.copy(
                        animationFrames = LIZARD_IDLE_FRAMES,
                        currentFrameIndex = 0, isAnimated = true,
                        currentPunchType = null, currentHandSide = null, isPunching = false,
                        currentDefenseType = null, isDefending = false,
                        playAnimationOnce = false,
                        sizeScale = LIZARD_SCALE
                    )
                }
                return@LaunchedEffect
            }
            val frames = getLizardPunchFrames(handSide, punchType)
            if (frames.isNotEmpty()) {
                sprites[index] = lizardSprite.copy(
                    animationFrames = frames,
                    currentFrameIndex = 0, isAnimated = true,
                    currentPunchType = punchType, currentHandSide = handSide, isPunching = true,
                    currentDefenseType = null, isDefending = false,
                    sizeScale = LIZARD_SCALE
                )
                pendingLizardImpactCheck = ImpactCheckData(punchType, handSide, frames.size)
                lastLizardPunchTime = lastLizardPunchTime + (punchType to System.currentTimeMillis())
            } else {
                // #region agent log
                agentLog("MainActivity:LizardSpriteUpdate", "set_idle", "{\"path\":\"punch_frames_empty\",\"hasPlayAnimationOnce\":false}", "A")
                // #endregion
                sprites[index] = lizardSprite.copy(
                    animationFrames = LIZARD_IDLE_FRAMES,
                    currentFrameIndex = 0, isAnimated = true,
                    currentPunchType = null, currentHandSide = null, isPunching = false,
                    playAnimationOnce = false,
                    sizeScale = LIZARD_SCALE
                )
            }
        } else {
            // #region agent log
            agentLog("MainActivity:LizardSpriteUpdate", "set_idle", "{\"path\":\"no_punch_else\",\"hasPlayAnimationOnce\":false}", "A")
            // #endregion
            sprites[index] = lizardSprite.copy(
                animationFrames = LIZARD_IDLE_FRAMES,
                currentFrameIndex = 0, isAnimated = true,
                currentPunchType = null, currentHandSide = null, isPunching = false,
                playAnimationOnce = false,
                sizeScale = LIZARD_SCALE
            )
        }
    }
    
    // Hit detection: Satoshi's punch vs Lizard (at 2nd-last frame)
    LaunchedEffect(pendingSatoshiImpactCheck, sprites, lizardInDamage) {
        val pending = pendingSatoshiImpactCheck ?: return@LaunchedEffect
        val impactDelay = maxOf(0, (pending.frameCount - 2)) * ANIMATION_FRAME_DELAY_MS
        delay(impactDelay)
        if (lizardInDamage) {
            pendingSatoshiImpactCheck = null
            return@LaunchedEffect
        }
        val lizardSprite = sprites.find { it.spriteType == SpriteType.LIZARD }
        if (lizardSprite != null) {
            val requiredDefense = getRequiredDefenseForAttack(pending.punchType, pending.handSide)
            val defended = lizardSprite.isDefending && lizardSprite.currentDefenseType == requiredDefense
            if (!defended) {
                if (lizardKOPhase != null) {
                    pendingSatoshiImpactCheck = null
                    return@LaunchedEffect
                }
                val damageType = getDamageTypeForAttack(pending.punchType, pending.handSide)
                if (lizardSprite.isDefending) {
                    // Defer damage until Lizard's defense animation completes
                    pendingLizardDamageAfterDefense = damageType
                    pendingLizardDamagePunchType = pending.punchType
                    pendingSatoshiImpactCheck = null
                    return@LaunchedEffect
                }
                val lizardIndex = sprites.indexOf(lizardSprite)
                lizardDamagePoints = (lizardDamagePoints + getDamagePoints(pending.punchType)).coerceAtMost(MAX_DAMAGE_POINTS)
                if (lizardDamagePoints >= MAX_DAMAGE_POINTS && lizardKOPhase == null) {
                    lizardKOPhase = KOPhase.FALL
                    val koFrames = getLizardKOPhaseFrames(KOPhase.FALL)
                    sprites[lizardIndex] = lizardSprite.copy(
                        animationFrames = koFrames,
                        currentFrameIndex = 0, isAnimated = true,
                        currentPunchType = null, currentHandSide = null, isPunching = false,
                        currentDefenseType = null, isDefending = false,
                        playAnimationOnce = true,
                        sizeScale = LIZARD_SCALE
                    )
                } else {
                    lizardInDamage = true
                    lizardDamageType = damageType
                    val damageFrames = getLizardDamageFrames(lizardDamageType!!)
                    // #region agent log
                    val isBody = damageType == DamageAnimationType.LEFT_DAMAGE_BODY || damageType == DamageAnimationType.RIGHT_DAMAGE_BODY
                    if (isBody) agentLog("MainActivity:HitApply", "lizard_body_damage", "{\"damageType\":\"$damageType\",\"frameCount\":${damageFrames.size},\"ts\":${System.currentTimeMillis()}}", "H1")
                    // #endregion
                    sprites[lizardIndex] = lizardSprite.copy(
                        animationFrames = if (damageFrames.isNotEmpty()) damageFrames else LIZARD_IDLE_FRAMES,
                        currentFrameIndex = 0, isAnimated = true,
                        currentPunchType = null, currentHandSide = null, isPunching = false,
                        currentDefenseType = null, isDefending = false,
                        playAnimationOnce = damageFrames.isNotEmpty(),
                        sizeScale = LIZARD_SCALE
                    )
                }
            }
        }
        pendingSatoshiImpactCheck = null
    }
    
    // Hit detection: Lizard's punch vs Satoshi (at 2nd-last frame)
    LaunchedEffect(pendingLizardImpactCheck, sprites, satoshiInDamage) {
        val pending = pendingLizardImpactCheck ?: return@LaunchedEffect
        val impactDelay = maxOf(0, (pending.frameCount - 2)) * ANIMATION_FRAME_DELAY_MS
        delay(impactDelay)
        if (satoshiInDamage) {
            pendingLizardImpactCheck = null
            return@LaunchedEffect
        }
        val satoshiSprite = sprites.find { it.spriteType == SpriteType.SATOSHI }
        if (satoshiSprite != null) {
            val requiredDefense = getRequiredDefenseForAttack(pending.punchType, pending.handSide)
            val defended = satoshiSprite.isDefending && satoshiSprite.currentDefenseType == requiredDefense
            if (!defended) {
                if (satoshiKOPhase != null) {
                    pendingLizardImpactCheck = null
                    return@LaunchedEffect
                }
                val damageType = getDamageTypeForAttack(pending.punchType, pending.handSide)
                if (satoshiSprite.isDefending) {
                    // Defer damage until Satoshi's defense animation completes
                    pendingSatoshiDamageAfterDefense = damageType
                    pendingSatoshiDamagePunchType = pending.punchType
                    pendingLizardImpactCheck = null
                    return@LaunchedEffect
                }
                val satoshiIndex = sprites.indexOf(satoshiSprite)
                satoshiDamagePoints = (satoshiDamagePoints + getDamagePoints(pending.punchType)).coerceAtMost(MAX_DAMAGE_POINTS)
                if (satoshiDamagePoints >= MAX_DAMAGE_POINTS && satoshiKOPhase == null) {
                    satoshiKOPhase = KOPhase.FALL
                    val koFrames = getSatoshiKOPhaseFrames(KOPhase.FALL)
                    sprites[satoshiIndex] = satoshiSprite.copy(
                        animationFrames = koFrames,
                        currentFrameIndex = 0, isAnimated = true,
                        currentPunchType = null, currentHandSide = null, isPunching = false,
                        currentDefenseType = null, isDefending = false,
                        playAnimationOnce = true,
                        sizeScale = SATOSHI_SCALE
                    )
                } else {
                    val damageFrames = getSatoshiDamageFrames(damageType)
                    if (DAMAGE_DEBUG) {
                        Log.d("DamageDebug", "{\"h\":\"A,B,E\",\"loc\":\"hit_apply_satoshi\",\"damageType\":\"$damageType\",\"frameCount\":${damageFrames.size},\"ts\":${System.currentTimeMillis()}}")
                    }
                    satoshiInDamage = true
                    satoshiDamageType = damageType
                    if (damageType == DamageAnimationType.CENTER_DAMAGE_UPPERCUT) {
                        agentLog("MainActivity:HitApply", "satoshi_center_damage", "{\"frameCount\":${damageFrames.size},\"ts\":${System.currentTimeMillis()}}", "A")
                    }
                    if (ENABLE_BLOCKING_LOGS) Log.d("DefenseClear", "{\"source\":\"damage\",\"ts\":${System.currentTimeMillis()}}")
                    sprites[satoshiIndex] = satoshiSprite.copy(
                        animationFrames = if (damageFrames.isNotEmpty()) damageFrames else SATOSHI_IDLE_FRAMES,
                        currentFrameIndex = 0, isAnimated = true,
                        currentPunchType = null, currentHandSide = null, isPunching = false,
                        currentDefenseType = null, isDefending = false,
                        playAnimationOnce = damageFrames.isNotEmpty(),
                        sizeScale = SATOSHI_SCALE
                    )
                }
            }
        }
        pendingLizardImpactCheck = null
    }
    
    // Damage completion is driven by Sprite's onPlayOnceComplete callback (event-driven), not by timer.
    // Safety net: if callback never runs, clear damage after timeout so app cannot get stuck
    LaunchedEffect(satoshiInDamage) {
        if (!satoshiInDamage) return@LaunchedEffect
        delay(DAMAGE_COMPLETION_SAFETY_TIMEOUT_MS)
        if (satoshiInDamage) {
            val satoshiSprite = sprites.find { it.spriteType == SpriteType.SATOSHI }
            if (satoshiSprite != null) {
                val idx = sprites.indexOf(satoshiSprite)
                sprites[idx] = satoshiSprite.copy(
                    animationFrames = SATOSHI_IDLE_FRAMES,
                    currentFrameIndex = 0, isAnimated = true,
                    currentPunchType = null, currentHandSide = null, isPunching = false,
                    currentDefenseType = null, isDefending = false, playAnimationOnce = false,
                    sizeScale = SATOSHI_SCALE
                )
            }
            satoshiInDamage = false
            satoshiDamageType = null
            if (DAMAGE_DEBUG) {
                Log.d("DamageDebug", "{\"h\":\"A\",\"loc\":\"damage_completion_safety_net\",\"ts\":${System.currentTimeMillis()}}")
            }
            agentLog("MainActivity:DamageCompletion", "satoshi_cleared_safety_net", "{\"ts\":${System.currentTimeMillis()}}", "D")
        }
    }
    LaunchedEffect(lizardInDamage) {
        if (!lizardInDamage) return@LaunchedEffect
        delay(DAMAGE_COMPLETION_SAFETY_TIMEOUT_MS)
        if (lizardInDamage) {
            val lizardSprite = sprites.find { it.spriteType == SpriteType.LIZARD }
            if (lizardSprite != null) {
                val idx = sprites.indexOf(lizardSprite)
                sprites[idx] = lizardSprite.copy(
                    animationFrames = LIZARD_IDLE_FRAMES,
                    currentFrameIndex = 0, isAnimated = true,
                    currentPunchType = null, currentHandSide = null, isPunching = false,
                    currentDefenseType = null, isDefending = false, playAnimationOnce = false,
                    sizeScale = LIZARD_SCALE
                )
            }
            val wasBodyType = lizardDamageType == DamageAnimationType.LEFT_DAMAGE_BODY || lizardDamageType == DamageAnimationType.RIGHT_DAMAGE_BODY
            lizardInDamage = false
            lizardDamageType = null
            if (wasBodyType) agentLog("MainActivity:LizardDamageCompletion", "lizard_body_cleared_safety_net", "{\"ts\":${System.currentTimeMillis()}}", "H2")
        }
    }

    // KO sequence driven by display duration constants (Fall -> Knocked Down -> Rise -> idle)
    LaunchedEffect(satoshiKOPhase != null) {
        if (satoshiKOPhase != KOPhase.FALL) return@LaunchedEffect
        delay(KO_FALL_DISPLAY_MS)
        satoshiKOPhase = KOPhase.KNOCKED_DOWN
        val satoshiSprite1 = sprites.find { it.spriteType == SpriteType.SATOSHI }
        if (satoshiSprite1 != null) {
            val idx = sprites.indexOf(satoshiSprite1)
            val koFrames = getSatoshiKOPhaseFrames(KOPhase.KNOCKED_DOWN)
            sprites[idx] = satoshiSprite1.copy(
                animationFrames = koFrames, currentFrameIndex = 0, isAnimated = true,
                currentPunchType = null, currentHandSide = null, isPunching = false,
                currentDefenseType = null, isDefending = false, playAnimationOnce = true,
                sizeScale = SATOSHI_SCALE
            )
        }
        delay(KO_KNOCKED_DOWN_DISPLAY_MS)
        satoshiKOPhase = KOPhase.RISE
        val satoshiSprite2 = sprites.find { it.spriteType == SpriteType.SATOSHI }
        if (satoshiSprite2 != null) {
            val idx = sprites.indexOf(satoshiSprite2)
            val koFrames = getSatoshiKOPhaseFrames(KOPhase.RISE)
            sprites[idx] = satoshiSprite2.copy(
                animationFrames = koFrames, currentFrameIndex = 0, isAnimated = true,
                currentPunchType = null, currentHandSide = null, isPunching = false,
                currentDefenseType = null, isDefending = false, playAnimationOnce = true,
                sizeScale = SATOSHI_SCALE
            )
        }
        delay(KO_RISE_DISPLAY_MS)
        satoshiKOPhase = null
        satoshiDamagePoints = 0
        satoshiInDamage = false
        satoshiDamageType = null
        val satoshiSprite3 = sprites.find { it.spriteType == SpriteType.SATOSHI }
        if (satoshiSprite3 != null) {
            val idx = sprites.indexOf(satoshiSprite3)
            sprites[idx] = satoshiSprite3.copy(
                animationFrames = SATOSHI_IDLE_FRAMES, currentFrameIndex = 0, isAnimated = true,
                currentPunchType = null, currentHandSide = null, isPunching = false,
                currentDefenseType = null, isDefending = false, playAnimationOnce = false,
                sizeScale = SATOSHI_SCALE
            )
        }
    }
    LaunchedEffect(lizardKOPhase != null) {
        if (lizardKOPhase != KOPhase.FALL) return@LaunchedEffect
        delay(KO_FALL_DISPLAY_MS)
        lizardKOPhase = KOPhase.KNOCKED_DOWN
        val lizardSprite1 = sprites.find { it.spriteType == SpriteType.LIZARD }
        if (lizardSprite1 != null) {
            val idx = sprites.indexOf(lizardSprite1)
            val koFrames = getLizardKOPhaseFrames(KOPhase.KNOCKED_DOWN)
            sprites[idx] = lizardSprite1.copy(
                animationFrames = koFrames, currentFrameIndex = 0, isAnimated = true,
                currentPunchType = null, currentHandSide = null, isPunching = false,
                currentDefenseType = null, isDefending = false, playAnimationOnce = true,
                sizeScale = LIZARD_SCALE
            )
        }
        delay(KO_KNOCKED_DOWN_DISPLAY_MS)
        lizardKOPhase = KOPhase.RISE
        val lizardSprite2 = sprites.find { it.spriteType == SpriteType.LIZARD }
        if (lizardSprite2 != null) {
            val idx = sprites.indexOf(lizardSprite2)
            val koFrames = getLizardKOPhaseFrames(KOPhase.RISE)
            sprites[idx] = lizardSprite2.copy(
                animationFrames = koFrames, currentFrameIndex = 0, isAnimated = true,
                currentPunchType = null, currentHandSide = null, isPunching = false,
                currentDefenseType = null, isDefending = false, playAnimationOnce = true,
                sizeScale = LIZARD_SCALE
            )
        }
        delay(KO_RISE_DISPLAY_MS)
        lizardKOPhase = null
        lizardDamagePoints = 0
        lizardInDamage = false
        lizardDamageType = null
        val lizardSprite3 = sprites.find { it.spriteType == SpriteType.LIZARD }
        if (lizardSprite3 != null) {
            val idx = sprites.indexOf(lizardSprite3)
            sprites[idx] = lizardSprite3.copy(
                animationFrames = LIZARD_IDLE_FRAMES, currentFrameIndex = 0, isAnimated = true,
                currentPunchType = null, currentHandSide = null, isPunching = false,
                currentDefenseType = null, isDefending = false, playAnimationOnce = false,
                sizeScale = LIZARD_SCALE
            )
        }
    }

    // Watch for punch animation completion - only trigger when new punch starts
    val satoshiSprite = sprites.find { it.spriteType == SpriteType.SATOSHI }
    val animationCompletionKey = remember(satoshiSprite?.isPunching, satoshiSprite?.currentPunchType, satoshiSprite?.currentHandSide) {
        if (satoshiSprite?.isPunching == true && satoshiSprite.currentPunchType != null && satoshiSprite.currentHandSide != null) {
            "${satoshiSprite.currentPunchType}_${satoshiSprite.currentHandSide}"
        } else {
            null
        }
    }
    
    LaunchedEffect(animationCompletionKey) {
        if (animationCompletionKey != null) {
            val sprite = sprites.find { it.spriteType == SpriteType.SATOSHI }
            if (sprite != null && sprite.animationFrames.isNotEmpty()) {
                val frameCount = sprite.animationFrames.size
                val animationDuration = frameCount * ANIMATION_FRAME_DELAY_MS
                
                if (ENABLE_PUNCH_LOGS) {
                    Log.d("PunchDebug", "Waiting for animation to complete - frames: $frameCount, duration: ${animationDuration}ms")
                }
                
                delay(animationDuration)
                
                // Animation completed - clear all punch state and transition to idle (never overwrite active defense)
                val updatedSprite = sprites.find { it.spriteType == SpriteType.SATOSHI }
                if (updatedSprite != null && updatedSprite.isPunching && !updatedSprite.isDefending) {
                    val index = sprites.indexOf(updatedSprite)
                    if (ENABLE_PUNCH_LOGS) {
                        Log.d("PunchDebug", "Animation completed - clearing punch state and transitioning to idle")
                    }
                    sprites[index] = updatedSprite.copy(
                        animationFrames = SATOSHI_IDLE_FRAMES,
                        currentFrameIndex = 0,
                        currentPunchType = null,
                        currentHandSide = null,
                        isPunching = false,
                        currentDefenseType = null,
                        isDefending = false,
                        sizeScale = SATOSHI_SCALE
                    )
                }
            }
        }
    }
    
    // Watch for defense animation completion - key includes defense type so switching type restarts timer (fixes split-second dodge)
    val defenseAnimationCompletionKey = remember(satoshiSprite?.isDefending, satoshiSprite?.currentDefenseType) {
        if (satoshiSprite?.isDefending == true && satoshiSprite?.currentDefenseType != null)
            "defense_${satoshiSprite.currentDefenseType}" else null
    }
    
    LaunchedEffect(defenseAnimationCompletionKey) {
        if (defenseAnimationCompletionKey != null) {
            val sprite = sprites.find { it.spriteType == SpriteType.SATOSHI }
            if (sprite != null && sprite.animationFrames.isNotEmpty()) {
                val frameCount = sprite.animationFrames.size
                // Enforce minimum 3 frames (full dodge/block length) so split-second clears don't happen
                val minDefenseFrames = 3
                val animationDuration = maxOf(minDefenseFrames * ANIMATION_FRAME_DELAY_MS, frameCount * ANIMATION_FRAME_DELAY_MS)
                val startTs = System.currentTimeMillis()
                if (ENABLE_BLOCKING_LOGS) Log.d("DefenseClear", "{\"source\":\"defense_completion_start\",\"key\":\"$defenseAnimationCompletionKey\",\"frameCount\":$frameCount,\"durationMs\":$animationDuration,\"ts\":$startTs}")
                if (ENABLE_BLOCKING_LOGS) {
                    Log.d("BlockingDebug", "Waiting for defense animation to complete - frames: $frameCount, duration: ${animationDuration}ms")
                }
                delay(animationDuration)
                // Animation completed - clear defense state and transition to idle
                val updatedSprite = sprites.find { it.spriteType == SpriteType.SATOSHI }
                if (updatedSprite != null && updatedSprite.isDefending) {
                    val index = sprites.indexOf(updatedSprite)
                    if (ENABLE_BLOCKING_LOGS) Log.d("DefenseClear", "{\"source\":\"defense_completion\",\"key\":\"$defenseAnimationCompletionKey\",\"ts\":${System.currentTimeMillis()},\"elapsedMs\":${System.currentTimeMillis() - startTs}}")
                    if (ENABLE_BLOCKING_LOGS) {
                        Log.d("BlockingDebug", "Defense animation completed - clearing defense state and transitioning to idle")
                    }
                    // Apply pending damage first (before writing idle) so LaunchedEffect isn't cancelled before this runs
                    val pendingDamage = pendingSatoshiDamageAfterDefense
                    val pendingPunch = pendingSatoshiDamagePunchType
                    if (pendingDamage != null && pendingPunch != null) {
                        pendingSatoshiDamageAfterDefense = null
                        pendingSatoshiDamagePunchType = null
                        if (satoshiKOPhase != null) {
                            // KO sequence owns sprite; do not overwrite with idle
                        } else {
                            satoshiDamagePoints = (satoshiDamagePoints + getDamagePoints(pendingPunch)).coerceAtMost(MAX_DAMAGE_POINTS)
                            if (satoshiDamagePoints >= MAX_DAMAGE_POINTS && satoshiKOPhase == null) {
                                satoshiKOPhase = KOPhase.FALL
                                val koFrames = getSatoshiKOPhaseFrames(KOPhase.FALL)
                                sprites[index] = updatedSprite.copy(
                                    animationFrames = koFrames, currentFrameIndex = 0, isAnimated = true,
                                    currentPunchType = null, currentHandSide = null, isPunching = false,
                                    currentDefenseType = null, isDefending = false,
                                    playAnimationOnce = true,
                                    sizeScale = SATOSHI_SCALE
                                )
                            } else {
                                val damageFrames = getSatoshiDamageFrames(pendingDamage)
                                satoshiInDamage = true
                                satoshiDamageType = pendingDamage
                                if (pendingDamage == DamageAnimationType.CENTER_DAMAGE_UPPERCUT) {
                                    agentLog("MainActivity:DefenseCompletion", "satoshi_center_damage_deferred", "{\"frameCount\":${damageFrames.size},\"ts\":${System.currentTimeMillis()}}", "A")
                                }
                                sprites[index] = updatedSprite.copy(
                                    animationFrames = if (damageFrames.isNotEmpty()) damageFrames else SATOSHI_IDLE_FRAMES,
                                    currentFrameIndex = 0, isAnimated = true,
                                    currentPunchType = null, currentHandSide = null, isPunching = false,
                                    currentDefenseType = null, isDefending = false,
                                    playAnimationOnce = damageFrames.isNotEmpty(),
                                    sizeScale = SATOSHI_SCALE
                                )
                            }
                        }
                    } else {
                        sprites[index] = updatedSprite.copy(
                            animationFrames = SATOSHI_IDLE_FRAMES,
                            currentFrameIndex = 0,
                            isAnimated = true,
                            currentDefenseType = null,
                            isDefending = false,
                            playAnimationOnce = false,
                            sizeScale = SATOSHI_SCALE
                        )
                    }
                    lastDefenseCompletionTime = System.currentTimeMillis()
                    lastDefenseType = null
                    lastDefenseSwitchTime = 0L
                }
            }
        }
    }
    
    // Lizard punch and defense completion
    val lizardSpriteForCompletion = sprites.find { it.spriteType == SpriteType.LIZARD }
    val lizardPunchCompletionKey = remember(lizardSpriteForCompletion?.isPunching, lizardSpriteForCompletion?.currentPunchType, lizardSpriteForCompletion?.currentHandSide) {
        if (lizardSpriteForCompletion?.isPunching == true && lizardSpriteForCompletion.currentPunchType != null && lizardSpriteForCompletion.currentHandSide != null)
            "lizard_${lizardSpriteForCompletion.currentPunchType}_${lizardSpriteForCompletion.currentHandSide}" else null
    }
    LaunchedEffect(lizardPunchCompletionKey) {
        if (lizardPunchCompletionKey != null) {
            val sprite = sprites.find { it.spriteType == SpriteType.LIZARD }
            if (sprite != null && sprite.animationFrames.isNotEmpty()) {
                delay(sprite.animationFrames.size * ANIMATION_FRAME_DELAY_MS)
                val updated = sprites.find { it.spriteType == SpriteType.LIZARD }
                if (updated != null && updated.isPunching) {
                    val idx = sprites.indexOf(updated)
                    // #region agent log
                    agentLog("MainActivity:LizardPunchCompletion", "set_idle", "{\"path\":\"punch_completion\",\"hasPlayAnimationOnce\":false}", "C")
                    // #endregion
                    sprites[idx] = updated.copy(
                        animationFrames = LIZARD_IDLE_FRAMES, currentFrameIndex = 0,
                        isAnimated = true, playAnimationOnce = false,
                        currentPunchType = null, currentHandSide = null, isPunching = false,
                        currentDefenseType = null, isDefending = false, sizeScale = LIZARD_SCALE
                    )
                }
            }
        }
    }
    val lizardDefenseCompletionKey = remember(lizardSpriteForCompletion?.isDefending, lizardSpriteForCompletion?.currentDefenseType) {
        if (lizardSpriteForCompletion?.isDefending == true && lizardSpriteForCompletion?.currentDefenseType != null)
            "lizard_defense_${lizardSpriteForCompletion.currentDefenseType}" else null
    }
    LaunchedEffect(lizardDefenseCompletionKey) {
        if (lizardDefenseCompletionKey != null) {
            // #region agent log
            agentLog("MainActivity:LizardDefenseCompletion", "entry", "{\"key\":\"$lizardDefenseCompletionKey\"}", "D")
            // #endregion
            val sprite = sprites.find { it.spriteType == SpriteType.LIZARD }
            if (sprite != null && sprite.animationFrames.isNotEmpty()) {
                val frameCount = sprite.animationFrames.size
                val minDefenseFrames = 3
                val durationMs = maxOf(minDefenseFrames * ANIMATION_FRAME_DELAY_MS, frameCount * ANIMATION_FRAME_DELAY_MS)
                delay(durationMs)
                val updated = sprites.find { it.spriteType == SpriteType.LIZARD }
                if (updated != null && updated.isDefending) {
                    val idx = sprites.indexOf(updated)
                    // Apply pending damage first (before writing idle) so LaunchedEffect isn't cancelled before this runs
                    val pendingDamage = pendingLizardDamageAfterDefense
                    val pendingPunch = pendingLizardDamagePunchType
                    if (pendingDamage != null && pendingPunch != null) {
                        // #region agent log
                        agentLog("MainActivity:LizardDefenseCompletion", "apply_damage", "{\"pendingDamage\":\"$pendingDamage\"}", "E")
                        val lizardBody = pendingDamage == DamageAnimationType.LEFT_DAMAGE_BODY || pendingDamage == DamageAnimationType.RIGHT_DAMAGE_BODY
                        if (lizardBody) agentLog("MainActivity:LizardDefenseCompletion", "lizard_body_damage_deferred", "{\"pendingDamage\":\"$pendingDamage\",\"ts\":${System.currentTimeMillis()}}", "H1")
                        // #endregion
                        pendingLizardDamageAfterDefense = null
                        pendingLizardDamagePunchType = null
                        if (lizardKOPhase != null) {
                            // KO sequence owns sprite; do not overwrite with idle
                        } else {
                            lizardDamagePoints = (lizardDamagePoints + getDamagePoints(pendingPunch)).coerceAtMost(MAX_DAMAGE_POINTS)
                            if (lizardDamagePoints >= MAX_DAMAGE_POINTS && lizardKOPhase == null) {
                                lizardKOPhase = KOPhase.FALL
                                val koFrames = getLizardKOPhaseFrames(KOPhase.FALL)
                                sprites[idx] = updated.copy(
                                    animationFrames = koFrames, currentFrameIndex = 0, isAnimated = true,
                                    currentPunchType = null, currentHandSide = null, isPunching = false,
                                    currentDefenseType = null, isDefending = false,
                                    playAnimationOnce = true,
                                    sizeScale = LIZARD_SCALE
                                )
                            } else {
                                val damageFrames = getLizardDamageFrames(pendingDamage)
                                lizardInDamage = true
                                lizardDamageType = pendingDamage
                                sprites[idx] = updated.copy(
                                    animationFrames = if (damageFrames.isNotEmpty()) damageFrames else LIZARD_IDLE_FRAMES,
                                    currentFrameIndex = 0, isAnimated = true,
                                    currentPunchType = null, currentHandSide = null, isPunching = false,
                                    currentDefenseType = null, isDefending = false,
                                    playAnimationOnce = damageFrames.isNotEmpty(),
                                    sizeScale = LIZARD_SCALE
                                )
                            }
                        }
                    } else {
                        // #region agent log
                        agentLog("MainActivity:LizardDefenseCompletion", "set_idle", "{\"playAnimationOnce\":false}", "E")
                        // #endregion
                        sprites[idx] = updated.copy(
                            animationFrames = LIZARD_IDLE_FRAMES, currentFrameIndex = 0, isAnimated = true,
                            currentDefenseType = null, isDefending = false, playAnimationOnce = false,
                            sizeScale = LIZARD_SCALE
                        )
                    }
                    lastLizardDefenseCompletionTime = System.currentTimeMillis()
                    lastLizardDefenseType = null
                    lastLizardDefenseSwitchTime = 0L
                }
            }
        }
    }
    
    
    // Track screen size for spawning
    var screenSizeForSpawn by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current.density

    // bg2 signs: spawn a wave of 1, 2, or 3 signs at SIGN_SPAWN_INTERVAL_MS; at most one sign per Y row
    LaunchedEffect(SIGN_SPAWN_INTERVAL_MS, screenSizeForSpawn.width, screenSizeForSpawn.height) {
        if (screenSizeForSpawn.width <= 0f || screenSizeForSpawn.height <= 0f) return@LaunchedEffect
        while (true) {
            delay(SIGN_SPAWN_INTERVAL_MS)
            val w = screenSizeForSpawn.width
            val h = screenSizeForSpawn.height
            val marginPx = (SIGN_MARGIN_X_FRACTION * w).toFloat()
            val minX = marginPx
            val maxX = w - marginPx
            val rowFractions = listOf(SIGN_ROW_Y_FRACTION_1, SIGN_ROW_Y_FRACTION_2, SIGN_ROW_Y_FRACTION_3)
            val occupiedRows = signSpawns.map { it.rowIndex }.toSet()
            val availableRows = (0..2).filter { it !in occupiedRows }
            val count = (1..3).random().coerceIn(0, availableRows.size)
            if (count > 0) {
                val chosenRows = availableRows.shuffled().take(count)
                val baseId = System.nanoTime()
                val newSigns = chosenRows.mapIndexed { i, r ->
                    val xPx = minX + (maxX - minX) * kotlin.random.Random.nextFloat()
                    val yPx = h * rowFractions[r]
                    BtcSignSpawn(id = baseId + i, xPx = xPx, yPx = yPx, frameIndex = 0, rowIndex = r)
                }
                signSpawns = signSpawns + newSigns
            }
        }
    }

    // bg2 signs: advance frames 0->1->2->Kill every SIGN_FRAME_DELAY_MS
    LaunchedEffect(Unit) {
        while (true) {
            delay(SIGN_FRAME_DELAY_MS)
            signSpawns = signSpawns
                .map { it.copy(frameIndex = it.frameIndex + 1) }
                .filter { it.frameIndex < BUY_BTC_SIGN_FRAMES.size }
        }
    }

    // Spawn cat when trigger fires (block height or SPAWN_CAT timer); only one cat at a time
    LaunchedEffect(catSpawnTriggerCount, screenSizeForSpawn.width, screenSizeForSpawn.height) {
        if (catSpawnTriggerCount == 0 || screenSizeForSpawn.width <= 0f || screenSizeForSpawn.height <= 0f) return@LaunchedEffect
        if (sprites.any { it.spriteType == SpriteType.CAT }) return@LaunchedEffect
        val directionLeft = kotlin.random.Random.nextBoolean()
        val positionY = screenSizeForSpawn.height * CAT_SPAWN_Y_FACTOR
        val halfWidthPx = (CAT_SIZE_DP * density / 2f).toFloat()
        val positionX = if (directionLeft) {
            screenSizeForSpawn.width + CAT_OFFSCREEN_MARGIN_PX + halfWidthPx
        } else {
            -CAT_OFFSCREEN_MARGIN_PX - halfWidthPx
        }
        val catState = SpriteState()
        catState.position = Offset(positionX, positionY)
        val catFrames = if (directionLeft) E_CAT_LEFT_FRAMES else E_CAT_RIGHT_FRAMES
        val catSprite = SpriteData(
            spriteState = catState,
            spriteResourceId = catFrames[0],
            spriteType = SpriteType.CAT,
            layer = 3,
            sizeScale = 1f,
            spriteSizeDp = CAT_SIZE_DP,
            animationFrames = catFrames,
            currentFrameIndex = 0,
            isAnimated = true
        )
        sprites.add(catSprite)
        catDirectionLeft = directionLeft
    }

    // Move cat each frame; remove when off-screen
    LaunchedEffect(sprites.any { it.spriteType == SpriteType.CAT }, catDirectionLeft) {
        val catSprite = sprites.find { it.spriteType == SpriteType.CAT } ?: return@LaunchedEffect
        val dirLeft = catDirectionLeft ?: return@LaunchedEffect
        var catIndex = sprites.indexOf(catSprite)
        if (catIndex < 0) return@LaunchedEffect
        val halfWidthPx = (CAT_SIZE_DP * density / 2f).toFloat()
        while (sprites.getOrNull(catIndex)?.spriteType == SpriteType.CAT) {
            delay(ANIMATION_FRAME_DELAY_MS)
            val cat = sprites.getOrNull(catIndex) ?: break
            if (cat.spriteType != SpriteType.CAT) break
            val pos = cat.spriteState.position
            val newX = if (dirLeft) pos.x - CAT_SPEED else pos.x + CAT_SPEED
            val newState = SpriteState()
            newState.position = Offset(newX, pos.y)
            sprites[catIndex] = cat.copy(spriteState = newState)
            val offLeft = newX + halfWidthPx < -CAT_OFFSCREEN_MARGIN_PX
            val offRight = newX - halfWidthPx > screenSizeForSpawn.width + CAT_OFFSCREEN_MARGIN_PX
            if (offLeft || offRight) {
                sprites.removeAt(catIndex)
                catDirectionLeft = null
                break
            }
        }
    }

    // Initialize Satoshi and update position when constants or screen size change
    LaunchedEffect(screenSizeForSpawn.width, screenSizeForSpawn.height, SATOSHI_X_POSITION, SATOSHI_Y_POSITION, SATOSHI_SCALE) {
        if (screenSizeForSpawn.width > 0f && screenSizeForSpawn.height > 0f) {
            // Check if Satoshi already exists
            val satoshiIndex = sprites.indexOfFirst { it.spriteType == SpriteType.SATOSHI }
            val satoshiExists = satoshiIndex >= 0
            
            // Satoshi sprite is 128dp x 128dp
            val spriteSizeDp = 128f
            // Store position as sprite center (0.0 = left/top, 0.5 = center, 1.0 = right/bottom); Sprite draws with center anchor
            val positionX = screenSizeForSpawn.width * SATOSHI_X_POSITION
            val positionY = screenSizeForSpawn.height * SATOSHI_Y_POSITION
            // #region agent log
            val halfW = screenSizeForSpawn.width / 2f
            agentLog("LaunchedEffect:Satoshi", "position_computed", "{\"screenW\":${screenSizeForSpawn.width},\"positionX\":$positionX,\"positionY\":$positionY,\"SATOSHI_X\":$SATOSHI_X_POSITION,\"halfWidth\":$halfW,\"deltaFromCenter\":${positionX - halfW}}", "H1")
            // #endregion
            if (!satoshiExists) {
                // Create new Satoshi sprite
                val satoshiState = SpriteState()
                satoshiState.position = Offset(positionX, positionY)
                
                val satoshiSprite = SpriteData(
                    spriteState = satoshiState,
                    spriteResourceId = SATOSHI_IDLE_FRAMES[0],  // Default to first frame
                    spriteType = SpriteType.SATOSHI,
                    layer = 2,
                    sizeScale = SATOSHI_SCALE,  // Use constant instead of hardcoded 1.0f
                    spriteSizeDp = spriteSizeDp,
                    animationFrames = SATOSHI_IDLE_FRAMES,
                    currentFrameIndex = 0,
                    isAnimated = true
                )
                
                sprites.add(satoshiSprite)
            } else {
                // Update existing Satoshi position
                val existingSprite = sprites[satoshiIndex]
                existingSprite.spriteState.position = Offset(positionX, positionY)
                // Update the sprite in the list to trigger recomposition
                sprites[satoshiIndex] = existingSprite.copy(
                    sizeScale = SATOSHI_SCALE
                )
            }
        }
    }
    
    // Initialize Lizard and update position when constants or screen size change
    LaunchedEffect(screenSizeForSpawn.width, screenSizeForSpawn.height, LIZARD_X_POSITION, LIZARD_Y_POSITION, LIZARD_SCALE) {
        if (screenSizeForSpawn.width > 0f && screenSizeForSpawn.height > 0f) {
            val lizardIndex = sprites.indexOfFirst { it.spriteType == SpriteType.LIZARD }
            val lizardExists = lizardIndex >= 0
            
            val spriteSizeDp = 128f
            // Store position as sprite center; Sprite draws with center anchor
            val positionX = screenSizeForSpawn.width * LIZARD_X_POSITION
            val positionY = screenSizeForSpawn.height * LIZARD_Y_POSITION
            // #region agent log
            val halfW = screenSizeForSpawn.width / 2f
            agentLog("LaunchedEffect:Lizard", "position_computed", "{\"screenW\":${screenSizeForSpawn.width},\"positionX\":$positionX,\"positionY\":$positionY,\"LIZARD_X\":$LIZARD_X_POSITION,\"halfWidth\":$halfW,\"deltaFromCenter\":${positionX - halfW}}", "H1")
            // #endregion
            if (!lizardExists) {
                val lizardState = SpriteState()
                lizardState.position = Offset(positionX, positionY)
                val lizardSprite = SpriteData(
                    spriteState = lizardState,
                    spriteResourceId = LIZARD_IDLE_FRAMES[0],
                    spriteType = SpriteType.LIZARD,
                    layer = 1,
                    sizeScale = LIZARD_SCALE,
                    spriteSizeDp = spriteSizeDp,
                    animationFrames = LIZARD_IDLE_FRAMES,
                    currentFrameIndex = 0,
                    isAnimated = true
                )
                sprites.add(lizardSprite)
            } else {
                val existingSprite = sprites[lizardIndex]
                existingSprite.spriteState.position = Offset(positionX, positionY)
                sprites[lizardIndex] = existingSprite.copy(
                    sizeScale = LIZARD_SCALE
                )
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                screenSizeForSpawn = coordinates.size.toSize()
                // #region agent log
                agentLog("Box:onGloballyPositioned", "screen_size", "{\"width\":${screenSizeForSpawn.width},\"height\":${screenSizeForSpawn.height},\"halfWidth\":${screenSizeForSpawn.width / 2f}}", "H3")
                // #endregion
            }
    ) {
        // Background: when chart visible draw bg1 (chart) + bg0 (ring); else draw bg3, bg2, bg0 (ring)
        if (bg2Visible) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.fillMaxHeight(BG2_CHART_TOP_OFFSET_FRACTION))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(BG2_CHART_HEIGHT_FRACTION)
                ) {
                    BtcCandleChart(candles = candleData, showAxisLabels = BG2_SHOW_AXIS_LABELS)
                }
                Spacer(modifier = Modifier.fillMaxHeight(1f - BG2_CHART_TOP_OFFSET_FRACTION - BG2_CHART_HEIGHT_FRACTION))
            }
            if (RING_FRAMES.isNotEmpty()) {
                val idx = backgroundFrameIndices.getOrElse(0) { 0 }.coerceIn(0, RING_FRAMES.size - 1)
                Image(
                    painter = painterResource(RING_FRAMES[idx]),
                    contentDescription = "Ring",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter
                )
            }
        } else {
            if (AUDIENCE_FRAMES_BY_RING.isNotEmpty()) {
                val ringIdx = backgroundFrameIndices.getOrElse(0) { 0 }.coerceIn(0, AUDIENCE_FRAMES_BY_RING.size - 1)
                val audienceIdx = backgroundFrameIndices.getOrElse(2) { 0 } % 3
                val drawableId = AUDIENCE_FRAMES_BY_RING.getOrNull(ringIdx)?.getOrNull(audienceIdx)
                if (drawableId != null) {
                    Image(
                        painter = painterResource(drawableId),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.TopCenter
                    )
                }
            }
            // bg2 signs: draw each spawn (0->1->2->Kill)
            val signSizePx = (SIGN_SIZE_DP * density).toFloat()
            signSpawns.forEach { sign ->
                key(sign.id) {
                    val drawableId = BUY_BTC_SIGN_FRAMES.getOrNull(sign.frameIndex) ?: return@forEach
                    Image(
                        painter = painterResource(drawableId),
                        contentDescription = null,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (sign.xPx - signSizePx / 2f).toInt(),
                                    (sign.yPx - signSizePx / 2f).toInt()
                                )
                            }
                            .size(SIGN_SIZE_DP.dp)
                    )
                }
            }
            if (BACKGROUND_LAYER_2_FRAMES.isNotEmpty()) {
                val idx = backgroundFrameIndices.getOrElse(1) { 0 }.coerceIn(0, BACKGROUND_LAYER_2_FRAMES.size - 1)
                Image(
                    painter = painterResource(BACKGROUND_LAYER_2_FRAMES[idx]),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            if (RING_FRAMES.isNotEmpty()) {
                val idx = backgroundFrameIndices.getOrElse(0) { 0 }.coerceIn(0, RING_FRAMES.size - 1)
                Image(
                    painter = painterResource(RING_FRAMES[idx]),
                    contentDescription = "Ring",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter
                )
            }
        }
        // Render sprites in layer order (1=Lizard, 2=Satoshi, 3=Cat)
        val rangeY = BOBBING_MAX_Y_DOWN - BOBBING_MAX_Y_UP
        val tY = if (rangeY != 0f) (movementOffsetY - BOBBING_MAX_Y_UP) / rangeY else 0.5f
        sprites.sortedBy { it.layer }.forEach { spriteData ->
            key(spriteData.spriteType) {
            val xBobbingOffset = when (spriteData.spriteType) {
                SpriteType.SATOSHI -> movementOffsetX
                SpriteType.LIZARD -> -movementOffsetX
                SpriteType.CAT -> 0f
                else -> 0f
            }
            val yBobbingOffsetCat = if (spriteData.spriteType == SpriteType.CAT) 0f else movementOffsetY
            val depthForSprite = when (spriteData.spriteType) {
                SpriteType.SATOSHI -> (1f - SCALE_SMALLER_PERCENT_SATOSHI / 100f) +
                    tY * ((1f + SCALE_LARGER_PERCENT_SATOSHI / 100f) - (1f - SCALE_SMALLER_PERCENT_SATOSHI / 100f))
                SpriteType.LIZARD -> (1f - SCALE_SMALLER_PERCENT_LIZARD / 100f) +
                    tY * ((1f + SCALE_LARGER_PERCENT_LIZARD / 100f) - (1f - SCALE_SMALLER_PERCENT_LIZARD / 100f))
                else -> 1f
            }
            Sprite(
                spriteData = spriteData,
                xBobbingOffset = xBobbingOffset,
                yBobbingOffset = yBobbingOffsetCat,
                depthScaleMultiplier = depthForSprite,
                contentDescription = "Sprite",
                opponentInKO = when (spriteData.spriteType) {
                    SpriteType.SATOSHI -> lizardKOPhase != null
                    SpriteType.LIZARD -> satoshiKOPhase != null
                    else -> false
                },
                onPlayOnceComplete = callback@{ spriteType ->
                    // KO sequence is driven by LaunchedEffect timers; ignore callback for KO to avoid double-advance
                    if (spriteType == SpriteType.SATOSHI && satoshiKOPhase != null) return@callback
                    if (spriteType == SpriteType.LIZARD && lizardKOPhase != null) return@callback
                    // Normal damage clear
                    if (spriteType == SpriteType.SATOSHI && satoshiInDamage) {
                        val satoshiSprite = sprites.find { it.spriteType == SpriteType.SATOSHI }
                        if (satoshiSprite != null) {
                            val idx = sprites.indexOf(satoshiSprite)
                            sprites[idx] = satoshiSprite.copy(
                                animationFrames = SATOSHI_IDLE_FRAMES,
                                currentFrameIndex = 0, isAnimated = true,
                                currentPunchType = null, currentHandSide = null, isPunching = false,
                                currentDefenseType = null, isDefending = false, playAnimationOnce = false,
                                sizeScale = SATOSHI_SCALE
                            )
                        }
                        satoshiInDamage = false
                        satoshiDamageType = null
                        if (DAMAGE_DEBUG) {
                            Log.d("DamageDebug", "{\"h\":\"A\",\"loc\":\"damage_completion_cleared\",\"ts\":${System.currentTimeMillis()}}")
                        }
                        agentLog("MainActivity:DamageCompletion", "satoshi_cleared", "{\"ts\":${System.currentTimeMillis()}}", "D")
                    }
                    if (spriteType == SpriteType.LIZARD && lizardInDamage) {
                        val lizardSprite = sprites.find { it.spriteType == SpriteType.LIZARD }
                        if (lizardSprite != null) {
                            val idx = sprites.indexOf(lizardSprite)
                            sprites[idx] = lizardSprite.copy(
                                animationFrames = LIZARD_IDLE_FRAMES,
                                currentFrameIndex = 0, isAnimated = true,
                                currentPunchType = null, currentHandSide = null, isPunching = false,
                                currentDefenseType = null, isDefending = false, playAnimationOnce = false,
                                sizeScale = LIZARD_SCALE
                            )
                        }
                        val wasBodyType = lizardDamageType == DamageAnimationType.LEFT_DAMAGE_BODY || lizardDamageType == DamageAnimationType.RIGHT_DAMAGE_BODY
                        lizardInDamage = false
                        lizardDamageType = null
                        if (wasBodyType) agentLog("MainActivity:LizardDamageCompletion", "lizard_body_cleared", "{\"ts\":${System.currentTimeMillis()}}", "H2")
                    }
                }
            )
            }
        }
        
        // Price displays as overlays
        // Center top - Time (block height) and elapsed since last update
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        val priceFontSize = (screenHeightDp / 20 * 0.4).sp
        val blockHeightFontSize = (screenHeightDp / 20 * 0.8 * 0.9).sp  // 2× price font, reduced 10%
        val blockLabelFontSize = (blockHeightFontSize.value * 0.6 * 1.25 * 0.9).sp  // reduced 10%
        val timerFontSize = (priceFontSize.value * 0.6 * 1.25).sp  // same as Offense/Defense labels
        val elapsedMs = lastBlockHeightUpdateTimeMs?.let { System.currentTimeMillis() - it } ?: 0L
        val elapsedSeconds = (elapsedMs / 1000).toInt()
        val elapsedH = elapsedSeconds / 3600
        val elapsedM = (elapsedSeconds % 3600) / 60
        val elapsedS = elapsedSeconds % 60
        val elapsedString = "%02d:%02d:%02d".format(elapsedH, elapsedM, elapsedS)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Time",
                fontSize = blockLabelFontSize,
                color = Color(0xFFF7931A)  // Bitcoin orange
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = blockHeight?.toString() ?: "—",
                fontSize = blockHeightFontSize,
                color = if (blockHeightFlashOn) Color.White else Color(0xFFF7931A)  // Bitcoin orange, flash white on update
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = elapsedString,
                fontSize = timerFontSize,
                color = if (elapsedMs >= TIMER_FLASH_WHEN_ELAPSED_MS && timerOverTenMinFlashOn) Color.White else Color(0xFFF7931A)
            )
        }
        // Top left - Binance
        PriceDisplay(
            label = "Binance BTC-USDT",
            price = binancePrice,
            isConnected = binanceIsConnected,
            previousPrice = binancePreviousPrice,
            buyVolume = binanceBuyVolume,
            sellVolume = binanceSellVolume,
            maxVolume = maxVolume,
            volumeAnimating = binanceVolumeAnimating,
            modeLabel = if (isBinanceDefense) "Defense" else "Offense",
            modifier = Modifier.align(Alignment.TopStart),
            pulseDirection = PulseDirection.LEFT_TO_RIGHT,
            damagePoints = satoshiDamagePoints,
            showDamageBar = true,
            maxDamagePoints = MAX_DAMAGE_POINTS
        )
        
        // Top right - Coinbase
        PriceDisplay(
            label = "Coinbase BTC-USD",
            price = coinbasePrice,
            isConnected = coinbaseIsConnected,
            previousPrice = coinbasePreviousPrice,
            buyVolume = coinbaseBuyVolume,
            sellVolume = coinbaseSellVolume,
            maxVolume = maxVolume,
            volumeAnimating = coinbaseVolumeAnimating,
            modeLabel = if (isCoinbaseDefense) "Defense" else "Offense",
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalAlignment = Alignment.End,
            pulseDirection = PulseDirection.RIGHT_TO_LEFT,
            damagePoints = lizardDamagePoints,
            showDamageBar = true,
            maxDamagePoints = MAX_DAMAGE_POINTS
        )
    }
}

// Generic sprite composable - renders sprite at its position
@Composable
fun Sprite(
    spriteData: SpriteData,
    xBobbingOffset: Float = 0f,
    yBobbingOffset: Float = 0f,
    depthScaleMultiplier: Float = 1f,
    contentDescription: String = "Sprite",
    opponentInKO: Boolean = false,
    onPlayOnceComplete: ((SpriteType) -> Unit)? = null
) {
    val spriteSize = spriteData.spriteSizeDp.dp
    val visualSize = spriteSize * spriteData.sizeScale * depthScaleMultiplier
    
    // Track current frame for animated sprites
    var currentFrameIndex by remember { mutableStateOf(spriteData.currentFrameIndex) }
    var currentResourceId by remember { mutableStateOf(spriteData.spriteResourceId) }
    
    // Create unique key for animations - only changes when new punch, defense, damage/one-shot, or opponent KO state
    // Include opponentInKO so idle loop restarts when opponent exits KO (fixes frozen idle after KO)
    val punchAnimationKey = remember(spriteData.currentPunchType, spriteData.currentHandSide, spriteData.currentDefenseType, spriteData.playAnimationOnce, opponentInKO) {
        when {
            spriteData.currentPunchType != null && spriteData.currentHandSide != null -> {
                "${spriteData.currentPunchType}_${spriteData.currentHandSide}"
            }
            spriteData.currentDefenseType != null -> {
                "defense_${spriteData.currentDefenseType}"
            }
            else -> {
                "idle_${spriteData.animationFrames.hashCode()}_${spriteData.playAnimationOnce}_oppKO=$opponentInKO"
            }
        }
    }
    
    // Update resource ID when animation frames change (but don't restart animation)
    LaunchedEffect(spriteData.animationFrames) {
        if (spriteData.animationFrames.isNotEmpty()) {
            if (DAMAGE_DEBUG) {
                Log.d("DamageDebug", "{\"h\":\"C\",\"loc\":\"sprite_animationFrames_reset\",\"spriteType\":\"${spriteData.spriteType}\",\"framesHash\":${spriteData.animationFrames.hashCode()},\"ts\":${System.currentTimeMillis()}}")
        }
        currentResourceId = spriteData.animationFrames[0]
            currentFrameIndex = 0
        }
    }
    
    // Animate frames if sprite is animated - use unique key to prevent restarts
    // Remove spriteData.isAnimated from dependencies to prevent restarts during animation
    LaunchedEffect(punchAnimationKey) {
        if (spriteData.spriteType == SpriteType.LIZARD && spriteData.isAnimated && spriteData.animationFrames.isNotEmpty()) {
            // #region agent log
            val branch = when {
                spriteData.isPunching && spriteData.currentPunchType != null -> "punch"
                spriteData.playAnimationOnce -> "playOnce"
                else -> "loop"
            }
            agentLog("Sprite:LaunchedEffect", "Lizard_anim_branch", "{\"branch\":\"$branch\",\"playAnimationOnce\":${spriteData.playAnimationOnce},\"key\":\"$punchAnimationKey\"}", "E")
            // #endregion
        }
        if (spriteData.isAnimated && spriteData.animationFrames.isNotEmpty()) {
            if (spriteData.isPunching && spriteData.currentPunchType != null) {
                // Play punch animation once, then stop
                for (frameIndex in spriteData.animationFrames.indices) {
                    currentFrameIndex = frameIndex
                    currentResourceId = spriteData.animationFrames[frameIndex]
                    delay(ANIMATION_FRAME_DELAY_MS)
                }
            } else if (spriteData.playAnimationOnce) {
                // Play defense or damage animation once, then stop on last frame
                val frameCount = spriteData.animationFrames.size
                if (spriteData.spriteType == SpriteType.SATOSHI && frameCount in 3..5) {
                    agentLog("Sprite:playOnce", "Satoshi_damage_start", "{\"frameCount\":$frameCount,\"key\":\"$punchAnimationKey\",\"ts\":${System.currentTimeMillis()}}", "A")
                }
                // #region agent log
                if (spriteData.spriteType == SpriteType.LIZARD && frameCount == 3) agentLog("Sprite:playOnce", "Lizard_body_damage_start", "{\"frameCount\":$frameCount,\"key\":\"$punchAnimationKey\",\"ts\":${System.currentTimeMillis()}}", "H3")
                // #endregion
                for (frameIndex in spriteData.animationFrames.indices) {
                    if (DAMAGE_DEBUG) {
                        Log.d("DamageDebug", "{\"h\":\"C\",\"loc\":\"sprite_playOnce_frame\",\"spriteType\":\"${spriteData.spriteType}\",\"frameIndex\":$frameIndex,\"ts\":${System.currentTimeMillis()}}")
                    }
                    currentFrameIndex = frameIndex
                    currentResourceId = spriteData.animationFrames[frameIndex]
                    delay(ANIMATION_FRAME_DELAY_MS)
                }
                if (spriteData.spriteType == SpriteType.SATOSHI && frameCount in 3..5) {
                    agentLog("Sprite:playOnce", "Satoshi_damage_done", "{\"frameCount\":$frameCount,\"ts\":${System.currentTimeMillis()}}", "B")
                }
                // #region agent log
                if (spriteData.spriteType == SpriteType.LIZARD && frameCount == 3) agentLog("Sprite:playOnce", "Lizard_body_damage_done", "{\"frameCount\":$frameCount,\"ts\":${System.currentTimeMillis()}}", "H3")
                // #endregion
                onPlayOnceComplete?.invoke(spriteData.spriteType)
            } else {
                // Loop idle animation - sync to frame 0 when entering so no stale frame after key restart
                val frames = spriteData.animationFrames
                if (frames.isNotEmpty()) {
                    currentFrameIndex = 0
                    currentResourceId = frames[0]
                }
                while (true) {
                    if (frames.isEmpty()) break
                    currentFrameIndex = (currentFrameIndex + 1) % frames.size
                    currentResourceId = frames[currentFrameIndex]
                    delay(ANIMATION_FRAME_DELAY_MS)
                }
            }
        }
    }
    
    // Use animated frame or static resource
    val displayResourceId = if (spriteData.isAnimated && spriteData.animationFrames.isNotEmpty()) {
        currentResourceId
    } else {
        spriteData.spriteResourceId
    }
    
    Image(
        painter = painterResource(id = displayResourceId),
        contentDescription = contentDescription,
        modifier = Modifier
            .offset {
                // position is sprite center; use layout Density so half-size matches .size(visualSize)
                val halfPx = visualSize.toPx() / 2f
                val leftPx = spriteData.spriteState.position.x + xBobbingOffset - halfPx
                val topPx = spriteData.spriteState.position.y + yBobbingOffset - halfPx
                // #region agent log
                agentLog("Sprite:offset", "layout_offset", "{\"spriteType\":\"${spriteData.spriteType}\",\"positionX\":${spriteData.spriteState.position.x},\"positionY\":${spriteData.spriteState.position.y},\"halfPx\":$halfPx,\"xBobbing\":$xBobbingOffset,\"leftPx\":$leftPx,\"centerX\":${spriteData.spriteState.position.x + xBobbingOffset}}", "H2")
                // #endregion
                IntOffset(leftPx.toInt(), topPx.toInt())
            }
            .size(visualSize)
    )
}

// Helper function to find an empty spawn location on the screen
fun findEmptySpawnLocation(
    screenSize: Size,
    spriteSizePx: Float,
    existingSprites: List<SpriteData>
): Offset? {
    val maxAttempts = 50
    var attempts = 0
    
    while (attempts < maxAttempts) {
        // Generate random position
        val randomX = kotlin.random.Random.nextFloat() * (screenSize.width - spriteSizePx).coerceAtLeast(0f)
        val randomY = kotlin.random.Random.nextFloat() * (screenSize.height - spriteSizePx).coerceAtLeast(0f)
        val candidatePos = Offset(randomX, randomY)
        
        // Check if position overlaps with any existing sprite
        var hasOverlap = false
        for (sprite in existingSprites) {
            val existingCenter = sprite.spriteState.position
            val density = android.content.res.Resources.getSystem().displayMetrics.density
            val existingSizePx = sprite.spriteSizeDp * density * sprite.sizeScale
            val existingTopLeft = Offset(existingCenter.x - existingSizePx / 2f, existingCenter.y - existingSizePx / 2f)
            if (checkOverlap(candidatePos, existingTopLeft, existingSizePx)) {
                hasOverlap = true
                break
            }
        }
        
        // If no overlap found, return this position
        if (!hasOverlap) {
            return candidatePos
        }
        
        attempts++
    }
    
    // If no empty spot found after max attempts, return null
    return null
}

// Helper function to check if two sprites overlap
fun checkOverlap(
    pos1: Offset,
    pos2: Offset,
    size: Float
): Boolean {
    val left1 = pos1.x
    val right1 = pos1.x + size
    val top1 = pos1.y
    val bottom1 = pos1.y + size
    
    val left2 = pos2.x
    val right2 = pos2.x + size
    val top2 = pos2.y
    val bottom2 = pos2.y + size
    
    return !(right1 < left2 || left1 > right2 || bottom1 < top2 || top1 > bottom2)
}

// Determine punch type based on BUY volume percentage
// volume: Current BUY volume from exchange
// maxBuyVolume: Maximum BUY volume across both exchanges (for percentage calculation)
fun getPunchTypeFromVolume(volume: Double?, maxBuyVolume: Double): PunchType? {
    if (ENABLE_PUNCH_LOGS) {
        Log.d("PunchDebug", "getPunchTypeFromVolume - volume: $volume, maxBuyVolume: $maxBuyVolume")
    }
    
    if (volume == null || volume <= 0.0 || maxBuyVolume <= 0.0) {
        if (ENABLE_PUNCH_LOGS) {
            Log.d("PunchDebug", "getPunchTypeFromVolume - Returning null (volume=$volume, maxBuyVolume=$maxBuyVolume)")
        }
        return null
    }
    
    // Calculate volume as percentage of max BUY volume
    val volumePercent = (volume / maxBuyVolume).toFloat()
    
    val punchType = when {
        volumePercent >= VOLUME_PERCENT_UPPERCUT_MIN && volumePercent <= VOLUME_PERCENT_UPPERCUT_MAX -> PunchType.UPPERCUT
        volumePercent >= VOLUME_PERCENT_CROSS_MIN && volumePercent <= VOLUME_PERCENT_CROSS_MAX -> PunchType.CROSS
        volumePercent >= VOLUME_PERCENT_HOOK_MIN && volumePercent <= VOLUME_PERCENT_HOOK_MAX -> PunchType.HOOK
        volumePercent >= VOLUME_PERCENT_BODY_MIN && volumePercent <= VOLUME_PERCENT_BODY_MAX -> PunchType.BODY
        volumePercent >= VOLUME_PERCENT_JAB_MIN && volumePercent <= VOLUME_PERCENT_JAB_MAX -> PunchType.JAB
        else -> null  // Volume too low or invalid
    }
    
    if (ENABLE_PUNCH_LOGS) {
        Log.d("PunchDebug", "getPunchTypeFromVolume - volumePercent: ${(volumePercent * 100).toInt()}%, punchType: $punchType")
    }
    
    return punchType
}

// Determine defense type based on BUY volume percentages from both exchanges
// binanceBuyVolume: Current Binance BUY volume
// coinbaseBuyVolume: Current Coinbase BUY volume
// maxBinanceBuyVolume: Maximum Binance BUY volume (for percentage calculation)
// maxCoinbaseBuyVolume: Maximum Coinbase BUY volume (for percentage calculation)
fun getDefenseTypeFromVolume(
    binanceBuyVolume: Double?,
    coinbaseBuyVolume: Double?,
    maxBinanceBuyVolume: Double,
    maxCoinbaseBuyVolume: Double
): DefenseType? {
    // Calculate BUY volume percentages
    val binanceBuyPercent = if (binanceBuyVolume != null && maxBinanceBuyVolume > 0.0) {
        (binanceBuyVolume / maxBinanceBuyVolume).toFloat()
    } else {
        if (ENABLE_BLOCKING_LOGS) {
            Log.d("BlockingDebug", "getDefenseTypeFromVolume - returning null (Binance volume/max invalid: buy=$binanceBuyVolume, max=$maxBinanceBuyVolume)")
        }
        return null
    }
    
    val coinbaseBuyPercent = if (coinbaseBuyVolume != null && maxCoinbaseBuyVolume > 0.0) {
        (coinbaseBuyVolume / maxCoinbaseBuyVolume).toFloat()
    } else {
        if (ENABLE_BLOCKING_LOGS) {
            Log.d("BlockingDebug", "getDefenseTypeFromVolume - returning null (Coinbase volume/max invalid: buy=$coinbaseBuyVolume, max=$maxCoinbaseBuyVolume)")
        }
        return null
    }
    
    // For Head/Body Block: use highest BUY % to resolve conflicts between exchanges
    val maxBuyPercent = maxOf(binanceBuyPercent, coinbaseBuyPercent)
    
    // Check for Head Block: highest BUY % between 67-100%
    val isHeadBlock = maxBuyPercent >= DEFENSE_HEAD_BLOCK_MIN && maxBuyPercent <= DEFENSE_HEAD_BLOCK_MAX
    
    // Check for Body Block: highest BUY % between 24-66%
    val isBodyBlock = maxBuyPercent >= DEFENSE_BODY_BLOCK_MIN && maxBuyPercent <= DEFENSE_BODY_BLOCK_MAX
    
    // Check for Dodge Left: Binance between 0-33%
    val isDodgeLeft = binanceBuyPercent >= DEFENSE_DODGE_LEFT_MIN && 
                      binanceBuyPercent <= DEFENSE_DODGE_LEFT_MAX
    
    // Check for Dodge Right: Coinbase between 0-33%
    val isDodgeRight = coinbaseBuyPercent >= DEFENSE_DODGE_RIGHT_MIN && 
                       coinbaseBuyPercent <= DEFENSE_DODGE_RIGHT_MAX
    
    // Priority: Head Block > Body Block > Dodge (random if both eligible)
    val defenseType = when {
        isHeadBlock -> DefenseType.HEAD_BLOCK
        isBodyBlock -> DefenseType.BODY_BLOCK
        isDodgeLeft && isDodgeRight -> DefenseType.DODGE_LEFT  // deterministic when both in range
        isDodgeLeft -> DefenseType.DODGE_LEFT
        isDodgeRight -> DefenseType.DODGE_RIGHT
        else -> null
    }
    
    if (ENABLE_BLOCKING_LOGS) {
        if (defenseType == null) {
            Log.d("BlockingDebug", "getDefenseTypeFromVolume - no defense type - Binance BUY %: ${"%.2f".format(binanceBuyPercent * 100)}%, Coinbase BUY %: ${"%.2f".format(coinbaseBuyPercent * 100)}%")
        } else {
            Log.d("BlockingDebug", "getDefenseTypeFromVolume - Binance BUY %: ${"%.2f".format(binanceBuyPercent * 100)}%, Coinbase BUY %: ${"%.2f".format(coinbaseBuyPercent * 100)}%, defenseType: $defenseType")
        }
    }
    
    return defenseType
}

// Get punch animation frames based on hand and punch type
fun getPunchFrames(handSide: HandSide, punchType: PunchType): List<Int> {
    return when (handSide) {
        HandSide.LEFT -> when (punchType) {
            PunchType.JAB -> SATOSHI_LEFT_JAB_FRAMES
            PunchType.BODY -> SATOSHI_LEFT_BODY_FRAMES
            PunchType.HOOK -> SATOSHI_LEFT_HOOK_FRAMES
            PunchType.CROSS -> SATOSHI_LEFT_CROSS_FRAMES
            PunchType.UPPERCUT -> SATOSHI_LEFT_UPPERCUT_FRAMES
        }
        HandSide.RIGHT -> when (punchType) {
            PunchType.JAB -> SATOSHI_RIGHT_JAB_FRAMES
            PunchType.BODY -> SATOSHI_RIGHT_BODY_FRAMES
            PunchType.HOOK -> SATOSHI_RIGHT_HOOK_FRAMES
            PunchType.CROSS -> SATOSHI_RIGHT_CROSS_FRAMES
            PunchType.UPPERCUT -> SATOSHI_RIGHT_UPPERCUT_FRAMES
        }
    }
}

// Get Lizard punch animation frames (uses SELL volume)
fun getLizardPunchFrames(handSide: HandSide, punchType: PunchType): List<Int> {
    return when (handSide) {
        HandSide.LEFT -> when (punchType) {
            PunchType.JAB -> LIZARD_LEFT_JAB_FRAMES
            PunchType.BODY -> LIZARD_LEFT_BODY_FRAMES
            PunchType.HOOK -> LIZARD_LEFT_HOOK_FRAMES
            PunchType.CROSS -> LIZARD_LEFT_CROSS_FRAMES
            PunchType.UPPERCUT -> LIZARD_LEFT_UPPERCUT_FRAMES
        }
        HandSide.RIGHT -> when (punchType) {
            PunchType.JAB -> LIZARD_RIGHT_JAB_FRAMES
            PunchType.BODY -> LIZARD_RIGHT_BODY_FRAMES
            PunchType.HOOK -> LIZARD_RIGHT_HOOK_FRAMES
            PunchType.CROSS -> LIZARD_RIGHT_CROSS_FRAMES
            PunchType.UPPERCUT -> LIZARD_RIGHT_UPPERCUT_FRAMES
        }
    }
}

// Get Lizard defense animation frames
fun getLizardDefenseFrames(defenseType: DefenseType): List<Int> = when (defenseType) {
    DefenseType.HEAD_BLOCK -> LIZARD_HEAD_BLOCK_FRAMES
    DefenseType.BODY_BLOCK -> LIZARD_BODY_BLOCK_FRAMES
    DefenseType.DODGE_LEFT -> LIZARD_DODGE_LEFT_FRAMES
    DefenseType.DODGE_RIGHT -> LIZARD_DODGE_RIGHT_FRAMES
}

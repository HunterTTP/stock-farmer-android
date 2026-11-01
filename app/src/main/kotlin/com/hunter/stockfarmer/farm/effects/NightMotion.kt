package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.rules.Season
import java.time.Instant
import kotlin.math.hypot
import kotlin.math.sin

internal data class Torch(
    val point: Point,
    val reach: Double,
    val alpha: Double,
)

internal object NightLights {
    fun torches(
        state: GameState,
        time: Double,
        darkness: Double,
    ): List<Torch> =
        state.placedBuildings.filter { it.building.glowsAtNight }.map { placed ->
            val building = placed.building
            val phase = ((placed.origin.row * 73 + placed.origin.col * 19) % 100) / 100.0
            val tick = (time - phase).coerceAtLeast(0.0) % (0.85 + phase * 0.6)
            val blend =
                when {
                    tick < 0.35 -> 1 - smooth(tick / 0.35)
                    tick < 0.85 -> smooth((tick - 0.35) / 0.5)
                    else -> 1.0
                }
            Torch(
                Point(
                    (placed.origin.col + building.width / 2.0) * TILE,
                    WORLD - (placed.origin.row + building.height - 0.3) * TILE,
                ),
                (maxOf(building.width, building.height) * 0.6 + 1.6) * TILE * (0.96 + 0.06 * blend),
                darkness * (0.4 + 0.1 * blend),
            )
        }

    fun pulseColor(season: Season): RGB =
        when (season) {
            Season.spring -> RGB(102, 255, 128)
            Season.fall -> RGB(255, 153, 38)
            Season.winter -> RGB(153, 217, 255)
        }
}

internal data class PulseTile(
    val tile: GridPoint,
    val intensity: Double,
    val twinkle: Double,
    val pattern: Int,
)

internal class PulseMotion {
    private var day: Int? = null
    private var followsSession = false
    private var night: Int? = null
    private var revision: Long? = null
    var origin = Point(GameState.GRID_SIZE / 2.0, GameState.GRID_SIZE / 2.0)
        private set

    fun sync(
        night: Int?,
        revision: Long,
    ) {
        followsSession = true
        this.night = night
        if (this.revision != revision) {
            this.revision = revision
            day = null
        }
    }

    fun tiles(
        state: GameState,
        now: Instant,
        center: Point,
    ): List<PulseTile> {
        val wave = NightPulse.wave(now, state.clockOffset) ?: return emptyList()
        if (followsSession && wave.day != night) return emptyList()
        if (day != wave.day) {
            day = wave.day
            origin = center
        }
        val covered = state.farmland + state.water.keys + state.placedBuildings.flatMap { it.footprint }
        val reach = NightPulse.reach(origin.y, origin.x)
        return (0 until GameState.GRID_SIZE).flatMap { row ->
            (0 until GameState.GRID_SIZE).mapNotNull { col ->
                val tile = GridPoint(row, col)
                if (tile in covered) return@mapNotNull null
                val intensity =
                    NightPulse.intensity(
                        hypot(row + 0.5 - origin.y, col + 0.5 - origin.x),
                        wave.progress,
                        reach,
                    )
                if (intensity <= 0.01) return@mapNotNull null
                val hash = kotlin.math.abs(((row * 73856093) xor (col * 19349663)).toLong())
                PulseTile(tile, intensity, 0.7 + 0.3 * sin(wave.progress * 60 + hash % 628 / 100.0), (hash % 6).toInt())
            }
        }
    }
}

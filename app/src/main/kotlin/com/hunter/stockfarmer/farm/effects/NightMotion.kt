package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.farm.FarmRenderer
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

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

/** The glowing tiles of one frame of the wave, in arrays the next frame refills, so a frame allocates nothing. */
internal class PulseTiles {
    private val rows = IntArray(GRID_TILES)
    private val cols = IntArray(GRID_TILES)
    private val patterns = IntArray(GRID_TILES)
    private val intensities = DoubleArray(GRID_TILES)
    private val twinkles = DoubleArray(GRID_TILES)
    var size = 0
        private set

    fun row(index: Int) = rows[index]

    fun col(index: Int) = cols[index]

    fun intensity(index: Int) = intensities[index]

    fun twinkle(index: Int) = twinkles[index]

    fun pattern(index: Int) = patterns[index]

    fun clear() {
        size = 0
    }

    fun add(
        row: Int,
        col: Int,
        intensity: Double,
        twinkle: Double,
        pattern: Int,
    ) {
        rows[size] = row
        cols[size] = col
        intensities[size] = intensity
        twinkles[size] = twinkle
        patterns[size] = pattern
        size++
    }
}

private const val GRID_TILES = GameState.GRID_SIZE * GameState.GRID_SIZE

/** One point per tile, so asking what covers a tile looks it up without making a point each frame. */
private val gridPoints = Array(GRID_TILES) { GridPoint(it / GameState.GRID_SIZE, it % GameState.GRID_SIZE) }

/** How many tiles past the edges of the wave's band the search for glowing tiles reaches, far more than any rounding. */
private const val BAND_MARGIN = 1.0

internal class PulseMotion {
    private var day: Int? = null
    private var followsSession = false
    private var night: Int? = null
    private var revision: Long? = null
    private val tiles = PulseTiles()
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

    /**
     * The grass tiles glowing now, row by row, that a [view] shows at least an edge of; with no view, all of them. Only
     * the tiles near the wave's band are looked at, and the same [PulseTiles] comes back every frame, refilled.
     */
    fun tiles(
        state: GameState,
        now: Instant,
        center: Point,
        view: View? = null,
    ): PulseTiles {
        tiles.clear()
        val wave = NightPulse.wave(now, state.clockOffset) ?: return tiles
        if (followsSession && wave.day != night) return tiles
        if (day != wave.day) {
            day = wave.day
            origin = center
        }
        val reach = NightPulse.reach(origin.y, origin.x)
        val radius = NightPulse.radius(wave.progress, reach)
        val outer = radius + NightPulse.BAND_WIDTH + BAND_MARGIN
        val inner = radius - NightPulse.BAND_WIDTH - BAND_MARGIN
        val last = GameState.GRID_SIZE - 1
        var firstRow = maxOf(0, floor(origin.y - 0.5 - outer).toInt())
        var lastRow = minOf(last, ceil(origin.y - 0.5 + outer).toInt())
        var firstVisibleCol = 0
        var lastVisibleCol = last
        if (view != null) {
            firstRow = maxOf(firstRow, last - ceil(view.top / TILE).toInt())
            lastRow = minOf(lastRow, last - floor(view.bottom / TILE).toInt() + 1)
            firstVisibleCol = maxOf(0, floor(view.left / TILE).toInt() - 1)
            lastVisibleCol = minOf(last, ceil(view.right / TILE).toInt())
        }
        for (row in firstRow..lastRow) {
            val dy = row + 0.5 - origin.y
            val outerSpan = outer * outer - dy * dy
            if (outerSpan < 0) continue
            val outerHalf = sqrt(outerSpan)
            val firstCol = maxOf(firstVisibleCol, floor(origin.x - 0.5 - outerHalf).toInt())
            val lastCol = minOf(lastVisibleCol, ceil(origin.x - 0.5 + outerHalf).toInt())
            // The tiles well inside the band's inner edge, which the front has already left dark.
            val innerSpan = if (inner > 0) inner * inner - dy * dy else -1.0
            val innerHalf = if (innerSpan > 0) sqrt(innerSpan) else -1.0
            val firstDark = if (innerHalf > 0) ceil(origin.x - 0.5 - innerHalf).toInt() else Int.MAX_VALUE
            val lastDark = if (innerHalf > 0) floor(origin.x - 0.5 + innerHalf).toInt() else Int.MIN_VALUE
            var col = firstCol
            while (col <= lastCol) {
                if (col in firstDark..lastDark) {
                    col = lastDark + 1
                    continue
                }
                addTile(state, view, row, col, wave.progress, reach)
                col++
            }
        }
        return tiles
    }

    private fun addTile(
        state: GameState,
        view: View?,
        row: Int,
        col: Int,
        progress: Double,
        reach: Double,
    ) {
        if (view != null) {
            val x = col * TILE
            val y = FarmRenderer.worldY(row).toDouble()
            if (x + TILE < view.left || x > view.right || y + TILE < view.bottom || y > view.top) return
        }
        val tile = gridPoints[row * GameState.GRID_SIZE + col]
        if (tile in state.farmland || tile in state.water || state.building(covering = tile) != null) return
        val intensity = NightPulse.intensity(hypot(row + 0.5 - origin.y, col + 0.5 - origin.x), progress, reach)
        if (intensity <= 0.01) return
        val hash = abs(((row * 73856093) xor (col * 19349663)).toLong())
        tiles.add(row, col, intensity, 0.7 + 0.3 * sin(progress * 60 + hash % 628 / 100.0), (hash % 6).toInt())
    }
}

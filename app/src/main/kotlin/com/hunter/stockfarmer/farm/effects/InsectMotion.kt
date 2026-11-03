package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.Butterflies
import com.hunter.stockfarmer.core.rules.Fireflies
import com.hunter.stockfarmer.core.rules.Fireflies.TilePosition
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.core.util.nextDouble
import com.hunter.stockfarmer.core.util.nextUnit
import com.hunter.stockfarmer.core.util.smoothstep
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

internal class FlightPath(
    val points: List<TilePosition>,
    speed: Double,
) {
    val arrivals = mutableListOf(0.0)

    init {
        require(points.size >= 2)
        points.zipWithNext().forEach { (a, b) -> arrivals += arrivals.last() + a.distance(b) / speed }
    }

    val lifespan get() = arrivals.last()

    fun position(
        time: Double,
        eased: Boolean = false,
    ): TilePosition {
        val leg = arrivals.indexOfLast { it <= time }.coerceIn(0, points.size - 2)
        val duration = arrivals[leg + 1] - arrivals[leg]
        val progress = if (duration > 0) ((time - arrivals[leg]) / duration).coerceIn(0.0, 1.0) else 1.0
        val mix = if (eased) smoothstep(progress) else progress
        val a = points[leg]
        val b = points[leg + 1]
        return TilePosition(a.row + (b.row - a.row) * mix, a.col + (b.col - a.col) * mix)
    }
}

internal fun TilePosition.world() = Point(col * TILE, WORLD - row * TILE)

internal class FireflyMotion(
    flight: List<TilePosition>,
    private val random: RandomSource,
) {
    private val path = FlightPath(flight, Fireflies.SPEED)
    private val period = random.nextDouble(1.6, 2.6)
    private val phases = DoubleArray(4) { random.nextDouble(0.0, 6.3) }
    var age = 0.0
        private set
    val done get() = age >= path.lifespan
    val pixelAlpha get() = envelope(age, path.lifespan, 0.8)
    val haloAlpha get() = envelope(age, path.lifespan, 3.0) * Fireflies.glow(age, period) * 0.9
    val point: Point get() {
        val base = path.position(age, eased = true).world()
        return Point(
            base.x + 7 * sin(age * 1.1 + phases[0]) + 3 * sin(age * 2.3 + phases[2]),
            base.y + 6 * cos(age * 0.9 + phases[1]) + 3 * sin(age * 1.9 + phases[3]),
        )
    }

    fun advance(seconds: Double) {
        age += seconds
    }
}

internal class FireflyPopulation(
    private val random: RandomSource = SystemRandomSource,
) {
    val flies = mutableListOf<FireflyMotion>()
    var onSighting: (() -> Unit)? = null

    fun update(
        state: GameState,
        seconds: Double,
        darkness: Double,
    ) {
        if (visibility(darkness) == 0.0) flies.clear()
        flies.forEach { it.advance(seconds) }
        flies.removeAll { it.done }
        if (flies.size >= Fireflies.MAX_AT_ONCE ||
            random.nextUnit() >= Fireflies.spawnChancePerSecond(darkness) * seconds
        ) {
            return
        }
        val flight = Fireflies.flight(Fireflies.hideaways(state), random) ?: return
        flies += FireflyMotion(flight, random)
        onSighting?.invoke()
    }

    fun visibility(darkness: Double) = ((darkness - 0.25) / 0.35).coerceIn(0.0, 1.0)
}

internal class ButterflyMotion(
    flight: List<TilePosition>,
    landingPoints: List<TilePosition>,
    val wings: Butterflies.Wings,
    val releasedID: UUID?,
    private val random: RandomSource,
) {
    private var path = FlightPath(flight, Butterflies.SPEED)
    private var landings = landingTimes(landingPoints)
    private val phases = DoubleArray(3) { random.nextDouble(0.0, 6.3) }
    val hue = random.nextUnit()
    var age = 0.0
        private set
    var travel = 0.0
        private set
    private var resting = 0.0
    private var nextLanding = 0
    private var calm = 0.0
    private var flapClock = 0.0
    private var sleeping = false
    private var leaving = 0.8
    private var base = flight.first()
    var point = base.world()
        private set
    var facing = 0.0
        private set
    val done get() = releasedID == null && (travel >= path.lifespan || leaving <= 0)
    val alpha get() =
        envelope(age, if (releasedID == null) age + path.lifespan - travel else age + 0.8, 0.8) *
            (leaving / 0.8).coerceAtLeast(0.0)
    val tile get() = GridPoint(floor((WORLD - point.y) / TILE).toInt(), floor(point.x / TILE).toInt())
    val frame: Int get() {
        val flap = intArrayOf(0, 1, 2, 1)
        if (calm > 0.5) return flap[(flapClock * 2.2 * 4).toInt() % 4]
        val time = (flapClock + phases[2] / 6.3 * 4.2) % 4.2
        return if (time >= 2.6) 0 else flap[(time * 2.2 * 4).toInt() % 4]
    }

    private fun landingTimes(landingPoints: List<TilePosition>) =
        path.points.indices
            .filter {
                it > 0 &&
                    it < path.points.lastIndex &&
                    path.points[it] in landingPoints
            }.map { path.arrivals[it] }

    private fun schedule(
        flight: List<TilePosition>,
        landingPoints: List<TilePosition>,
    ) {
        path = FlightPath(flight, Butterflies.SPEED)
        landings = landingTimes(landingPoints)
        travel = 0.0
        nextLanding = 0
        resting = 0.0
    }

    fun guide(
        fine: Boolean,
        attractions: List<Butterflies.Attraction>,
        landingPoints: List<TilePosition>,
    ) {
        if (releasedID == null) return
        if (!fine && !sleeping) {
            sleeping = true
            schedule(listOf(base, Butterflies.restingSpot(base, attractions)), emptyList())
        } else if (fine && (sleeping || travel >= path.lifespan)) {
            sleeping = false
            schedule(Butterflies.wander(base, attractions, random), landingPoints)
        }
    }

    fun advance(
        seconds: Double,
        fine: Boolean,
    ) {
        age += seconds
        when {
            sleeping && travel >= path.lifespan -> {
                calm += (1 - calm) * minOf(1.0, seconds * 2)
            }

            resting > 0 -> {
                resting = (resting - seconds).coerceAtLeast(0.0)
                calm = 1.0
            }

            nextLanding < landings.size && travel + seconds >= landings[nextLanding] -> {
                travel = landings[nextLanding++]
                resting = random.nextDouble(2.5, 5.0)
                calm = 1.0
            }

            else -> {
                travel += seconds
                calm -= calm * minOf(1.0, seconds * 2)
            }
        }
        flapClock += seconds * (1 - 0.88 * calm)
        leaving = if (releasedID == null && !fine) leaving - seconds else minOf(0.8, leaving + seconds)
        base = path.position(travel)
        val ground = base.world()
        val spot =
            Point(
                ground.x + 9 * (1 - calm) * sin(age * 2.7 + phases[0]),
                ground.y + 8 * (1 - calm) * cos(age * 3.3 + phases[1]),
            )
        if (seconds > 0 &&
            kotlin.math.hypot(spot.x - point.x, spot.y - point.y) > 0.01
        ) {
            facing =
                atan2(spot.y - point.y, spot.x - point.x) * 180 / Math.PI - 90
        }
        point = spot
    }
}

internal class ButterflyPopulation(
    private val random: RandomSource = SystemRandomSource,
) {
    val flies = mutableListOf<ButterflyMotion>()

    fun update(
        state: GameState,
        seconds: Double,
        darkness: Double,
        rain: Double,
    ) {
        val attractions = Butterflies.attractions(state)
        val landings = attractions.flatMap { it.landingPoints }
        val released = state.releasedButterflies.associateBy { it.id }
        flies.removeAll { it.releasedID != null && it.releasedID !in released }
        val present = flies.mapNotNull { it.releasedID }.toSet()
        for (letGo in state.releasedButterflies) {
            if (letGo.id in present) continue
            val flight = Butterflies.wander(TilePosition.middleOf(letGo.home), attractions, random)
            flies += ButterflyMotion(flight, landings, letGo.wings, letGo.id, random)
        }
        val fine = darkness < 0.25 && rain < 0.15
        flies.forEach {
            it.guide(fine, attractions, landings)
            it.advance(seconds, fine && state.season != Season.winter)
        }
        flies.removeAll { it.done }
        if (flies.count { it.releasedID == null } >= Butterflies.MAX_AT_ONCE ||
            random.nextUnit() >= Butterflies.spawnChancePerSecond(darkness, rain, state.season) * seconds
        ) {
            return
        }
        val flight = Butterflies.flight(attractions, random)
        flies += ButterflyMotion(flight, landings, Butterflies.wings(random), null, random)
    }
}

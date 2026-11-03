package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.epochSecondsDouble
import com.hunter.stockfarmer.core.util.nextBoolean
import com.hunter.stockfarmer.core.util.nextUnit
import com.hunter.stockfarmer.core.util.randomElement
import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

/** How long a pond with room for a fish waits before its first, and between a fish being caught and the next, sparingly. */
internal const val FIRST_FISH_DELAY = 10.0 * 60

/** A fish that nobody catches swims away after one day on the farm's clock. */
internal val FISH_LIFETIME = DayNightCycle.secondsPerDay

/** A fish keeps within this many tiles of its saved home tile. */
private const val FISH_ROAM_RADIUS = 6

/** Swimming left or right is this many times as likely as swimming up or down. */
private const val SIDEWAYS_PREFERENCE = 4.0

/**
 * Brings every body of water up to date: each is given places to keep fish as its shape allows, a fish appears once its
 * place's time has come, and a place whose water has gone loses its timer. A fish's saved home becomes the tile it appears
 * on. A fish that has been there a day swims away and its place waits as long as after a catch; [onTheLine] names the
 * places whose fish are on the line, which stay.
 */
fun GameEngine.updateFish(
    now: Instant,
    onTheLine: Set<GridPoint> = emptySet(),
    using: RandomSource,
) {
    settleFishPonds(now)
    sendOldFishAway(now, onTheLine)
    val occupants = wildWaterOccupants
    for (anchor in state.fishPonds.keys.sortedWith(compareBy({ it.row }, { it.col }))) {
        val pond = state.fishPonds[anchor] ?: continue
        if (pond.fish != null || !isFishDue(pond, now) || occupants.fish.size >= animalLimitOnTheMap) continue
        val tile = newFishTile(anchor, occupants, using) ?: continue
        val kind = FishKind.random(state.level, using)
        settleFish(PondFish(kind.id, tile, kind.randomWeight(using), farmClock(now)), anchor, pond)
        occupants.add(Waterfolk.fish, tile)
    }
}

/**
 * Where ducks and the fish that appeared by themselves are, which is all newcomers are counted against and keep their
 * distance from: the fish the player put in are neither.
 */
private val GameEngine.wildWaterOccupants: WaterOccupants
    get() {
        val occupants = waterOccupants
        for (pond in state.fishPonds.values) {
            val fish = pond.fish
            if (fish != null && !fish.isNaturallyGenerated) occupants.remove(Waterfolk.fish, fish.position)
        }
        return occupants
    }

private fun GameEngine.farmClock(now: Instant): Instant = now.advancedBy(state.clockOffset)

private fun GameEngine.sendOldFishAway(
    now: Instant,
    onTheLine: Set<GridPoint>,
) {
    val clock = farmClock(now)
    for (anchor in state.fishPonds.keys.sortedWith(compareBy({ it.row }, { it.col }))) {
        val pond = state.fishPonds[anchor] ?: continue
        val fish = pond.fish ?: continue
        if (!fish.isNaturallyGenerated) continue
        val appearedAt = fish.appearedAt
        if (appearedAt == null) {
            state.fishPonds[anchor] = pond.copy(fish = fish.copy(appearedAt = clock))
            continue
        }
        if (anchor in onTheLine || clock < appearedAt.advancedBy(FISH_LIFETIME)) continue
        state.fishPonds[anchor] =
            pond.copy(fish = null, nextFishAt = now.advancedBy(GameEngine.FISH_INTERVAL), waitStartedAt = now)
    }
}

/** Darkness brings fish sooner: half the normal wait at full night, easing through twilight. */
private fun GameEngine.isFishDue(
    pond: FishPond,
    now: Instant,
): Boolean {
    val darkness = DayNightCycle.darkness(now, state.clockOffset)
    val wait = max(0.0, pond.nextFishAt.secondsSince(pond.waitStartedAt))
    return now >= pond.nextFishAt.advancedBy(-wait * darkness * 0.5)
}

/**
 * Keeps independent fish places in every body of water, as many as its interior has room for and the whole map has room
 * for. A place with a fish in it is never taken away.
 */
internal fun GameEngine.settleFishPonds(now: Instant) {
    val layout = state.waterLayout
    val kept = mutableMapOf<GridPoint, FishPond>()
    val placesInBody = mutableMapOf<Int, MutableList<Pair<GridPoint, FishPond>>>()
    for ((originalHome, originalRecord) in state.fishPonds.entries.sortedWith(
        compareBy({ it.key.row }, { it.key.col }),
    )) {
        var record = originalRecord
        var home = originalHome
        val fish = record.fish
        if (fish != null) {
            val kind = fish.kind
            if (fish.weight == null && kind != null) {
                record = record.copy(fish = fish.copy(weight = savedBeforeWeights(kind, home, record.nextFishAt)))
            }
            if (fish.position !in state.water) {
                val water = nearestWater(fish.position) ?: continue
                record = record.copy(fish = record.fish?.copy(position = water))
            }
            if (home !in state.water) record.fish?.position?.let { home = it }
        }
        val body = layout.bodyIndexByTile[record.fish?.position ?: home] ?: continue
        placesInBody.getOrPut(body) { mutableListOf() } += home to record
    }
    var places = 0
    for ((index, body) in layout.bodies.withIndex()) {
        val limit = animalLimit(index)
        val existing =
            (placesInBody[index] ?: mutableListOf()).sortedWith(
                compareBy({ if (it.second.fish != null) 0 else 1 }, { it.first.row }, { it.first.col }),
            )
        var count = 0
        for ((home, record) in existing) {
            if (record.fish == null && !(count < limit && places < animalLimitOnTheMap)) continue
            kept[home] = record
            // A fish the player put in takes up none of the room for fish that appear by themselves.
            if (record.fish?.isNaturallyGenerated == false) continue
            count += 1
            places += 1
        }
        for (tile in body.interior) {
            if (count < limit && places < animalLimitOnTheMap && tile !in kept && tile !in state.fishPonds) {
                kept[tile] = FishPond(nextFishAt = now.advancedBy(FIRST_FISH_DELAY), waitStartedAt = now)
                count += 1
                places += 1
            }
        }
    }
    state.fishPonds = kept
}

/** Where a place's next fish may appear: open water in the same body of water as the place. */
private fun GameEngine.newFishTile(
    anchor: GridPoint,
    occupants: WaterOccupants,
    using: RandomSource,
): GridPoint? {
    val body = state.waterLayout.bodyIndexByTile[anchor] ?: return null
    return openWaterTiles(Waterfolk.fish, body, occupants)
        .filter { (it == anchor || it !in state.fishPonds) && !isFishSwimming(it) }
        .randomElement(using)
}

/** A fish that has just appeared makes the tile it is on its home, which is what its place is saved under. */
private fun GameEngine.settleFish(
    fish: PondFish,
    anchor: GridPoint,
    pond: FishPond,
) {
    state.fishPonds.remove(anchor)
    state.fishPonds[fish.position] = pond.copy(fish = fish)
}

/** Each of a new map's fish places is occupied as likely as not. */
internal fun GameEngine.scatterPondFish(
    now: Instant,
    using: RandomSource,
) {
    settleFishPonds(now)
    val occupants = wildWaterOccupants
    for (anchor in state.fishPonds.keys.sortedWith(compareBy({ it.row }, { it.col }))) {
        if (!using.nextBoolean()) continue
        val pond = state.fishPonds[anchor] ?: continue
        if (occupants.fish.size >= animalLimitOnTheMap) continue
        val tile = newFishTile(anchor, occupants, using) ?: continue
        val kind = FishKind.random(state.level, using)
        settleFish(PondFish(kind.id, tile, kind.randomWeight(using), farmClock(now)), anchor, pond)
        occupants.add(Waterfolk.fish, tile)
    }
}

/**
 * Which way a pond's fish sets off: one of the four directions with water next to it inside its range. Null if it has no
 * fish or nowhere to swim.
 */
fun GameEngine.fishDirection(
    anchor: GridPoint,
    using: RandomSource,
): GridPoint? {
    val position = state.fishPonds[anchor]?.fish?.position ?: return null
    val options =
        position.neighbors.filter { canFishSwim(it, anchor) }.map {
            GridPoint(
                it.row - position.row,
                it.col - position.col,
            )
        }
    // A fish keeps to swimming sideways, and only now and then turns up or down.
    var roll = using.nextUnit() * max(options.sumOf(::sidewaysPreference), java.lang.Double.MIN_VALUE)
    for (option in options) {
        roll -= sidewaysPreference(option)
        if (roll < 0) return option
    }
    return options.lastOrNull()
}

private fun sidewaysPreference(direction: GridPoint): Double = if (direction.row == 0) SIDEWAYS_PREFERENCE else 1.0

/** Moves a pond's fish one tile in a direction, unless that isn't water or is out of its range. Returns where it went. */
fun GameEngine.moveFish(
    anchor: GridPoint,
    direction: GridPoint,
): GridPoint? {
    val pond = state.fishPonds[anchor] ?: return null
    val fish = pond.fish ?: return null
    val destination = GridPoint(fish.position.row + direction.row, fish.position.col + direction.col)
    if (abs(direction.row) + abs(direction.col) != 1 || !canFishSwim(destination, anchor)) return null
    state.fishPonds[anchor] = pond.copy(fish = fish.copy(position = destination))
    return destination
}

private fun GameEngine.canFishSwim(
    tile: GridPoint,
    anchor: GridPoint,
): Boolean =
    tile in state.water && state.decoration(tile) == null &&
        state.fishPonds.none { (key, value) -> key != anchor && value.fish?.position == tile } &&
        max(abs(tile.row - anchor.row), abs(tile.col - anchor.col)) <= FISH_ROAM_RADIUS

/** Whether a pond's fish is swimming on a tile, which only the Fishing Rod catches. */
internal fun GameEngine.isFishSwimming(point: GridPoint): Boolean =
    state.fishPonds.values.any {
        it.fish?.position ==
            point
    }

/**
 * Casts onto a tile: if a fish is swimming in that very tile, says so, and the fish is landed or lost in a
 * [FishingContest]. Nothing changes, and nothing is spent, until it is landed.
 */
fun GameEngine.castRod(point: GridPoint): List<GameEvent> =
    if (biting(point) == null) reject(Rejection.FishNotBiting) else listOf(GameEvent.FishHooked(point))

/** The kind of fish swimming in the tile at a point, if there is one. */
fun GameEngine.fishBiting(point: GridPoint): FishKind? = biting(point)?.second

/** How much the fish in a pond weighs, in pounds, within what its kind may: settled when the fish appears. */
fun GameEngine.fishWeight(anchor: GridPoint): Double? {
    val pond = state.fishPonds[anchor] ?: return null
    val fish = pond.fish ?: return null
    val kind = fish.kind ?: return null
    return fish.weight ?: savedBeforeWeights(kind, anchor, pond.nextFishAt)
}

/** The weight a fish saved before weights were kept has always had, worked out from the fish itself so it stays the same. */
internal fun savedBeforeWeights(
    kind: FishKind,
    anchor: GridPoint,
    nextFishAt: Instant,
): Double {
    val random = StageRandom("weight|${anchor.row},${anchor.col}|${kind.id}|${nextFishAt.epochSecondsDouble}")
    val range = kind.weightRange
    val middling = (random.next() + random.next()) / 2
    return ((range.start + middling * (range.endInclusive - range.start)) * 10).roundedHalfAwayFromZero() / 10
}

/** What the fish in a pond sells for: what its kind is worth, whatever it weighs. */
fun GameEngine.fishSaleValue(anchor: GridPoint): Double? =
    state.fishPonds[anchor]
        ?.fish
        ?.kind
        ?.value

/** The pond, by its anchor tile, whose fish is swimming in the tile at a point, which stays the one on the line wherever it swims. */
fun GameEngine.hookedPond(point: GridPoint): GridPoint? = biting(point)?.first

/** The player gives the fish of a pond up: it leaves the pond for good, and the next one is as long coming as after a catch. */
fun GameEngine.loseFish(
    anchor: GridPoint,
    now: Instant,
) {
    val pond = state.fishPonds[anchor] ?: return
    if (pond.fish == null) return
    state.fishPonds[anchor] =
        pond.copy(fish = null, nextFishAt = now.advancedBy(GameEngine.FISH_INTERVAL), waitStartedAt = now)
}

/**
 * The fish of a pond is landed, wherever it has swum to since: it comes out of the pond, and the next one is a while off.
 * It goes into storage, which must have room, or is sold on the spot, which needs none; either way it is worth what its
 * kind is worth and earns XP by its weight. [point] is where it was hooked, which is where the catch is shown to come from.
 */
fun GameEngine.landFish(
    anchor: GridPoint,
    point: GridPoint,
    now: Instant,
    selling: Boolean = false,
): List<GameEvent> {
    val pond = state.fishPonds[anchor]
    val caught = pond?.fish
    val kind = caught?.kind
    val value = fishSaleValue(anchor)
    if (pond == null || caught == null || kind == null || value == null) return reject(Rejection.FishNotBiting)
    if (!selling && storedCount >= storageCapacity) return reject(Rejection.StorageFull)
    val weight = fishWeight(anchor) ?: kind.weightRange.start
    state.fishPonds[anchor] =
        pond.copy(fish = null, nextFishAt = now.advancedBy(GameEngine.FISH_INTERVAL), waitStartedAt = now)
    val levelBefore = state.level
    val xp = if (caught.isNaturallyGenerated) LevelRules.fishXP(kind, weight, levelBefore) else 0
    state.xp += xp
    if (caught.isNaturallyGenerated) state.career.noteCatch(kind.id, weight)
    val item = StorageItem.Fish(kind)
    if (!selling) return listOf(gather(item, point, HarvestTool.rod, xp)) + levelUpEvents(levelBefore)
    recordQuestProgress(QuestGoal.Collect(item.id))
    markHarvested()
    credit(value)
    recordSale(item, 1, value)
    return listOf<GameEvent>(GameEvent.Sold(point, item.id, value, xp)) + levelUpEvents(levelBefore)
}

/** Notes that the hint on landing the first fish has been shown, so that it is only ever shown once on a farm. */
fun GameEngine.markFishingHintSeen() {
    state.hasSeenFishingHint = true
}

/** The pond whose fish is swimming in the tile at a point, by its anchor tile, and the kind of fish, if there is one. */
private fun GameEngine.biting(point: GridPoint): Pair<GridPoint, FishKind>? {
    for ((anchor, pond) in state.fishPonds) {
        val fish = pond.fish ?: continue
        val kind = fish.kind ?: continue
        if (fish.position == point) return anchor to kind
    }
    return null
}

package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.move
import com.hunter.stockfarmer.core.rules.name
import com.hunter.stockfarmer.core.rules.position
import com.hunter.stockfarmer.core.rules.readyProduce
import com.hunter.stockfarmer.core.rules.wanderDirection
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.nextInt
import com.hunter.stockfarmer.farm.animals.AnimalGesture
import com.hunter.stockfarmer.farm.animals.AnimalHitTarget
import com.hunter.stockfarmer.farm.animals.AnimalReaction
import com.hunter.stockfarmer.farm.animals.animalReaction
import com.hunter.stockfarmer.ui.sheets.AnimalInteraction
import com.hunter.stockfarmer.ui.sheets.SheetTarget
import java.time.Instant
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/** Scene requests cross threads here; all movement stays in the game driver and uses existing core rules. */
class AnimalSceneGestures(
    private val random: RandomSource = SystemRandomSource,
) {
    data class Petting(
        val animal: AnimalRef,
        val at: Instant,
    )

    private data class Request(
        val reaction: AnimalReaction,
        val at: Instant,
    )

    private data class Nudge(
        val remaining: Int,
        val next: Instant,
        val direction: GridPoint? = null,
    )

    private val requests = ConcurrentLinkedQueue<Request>()
    private val hearts = ConcurrentLinkedQueue<Petting>()
    private val nudging = ConcurrentLinkedQueue<AnimalRef>()
    private val nudges = mutableMapOf<AnimalRef, Nudge>()

    @Volatile var targets: List<AnimalHitTarget> = emptyList()

    fun interact(
        animal: AnimalRef,
        gesture: AnimalGesture,
        now: Instant,
    ): Boolean {
        val reaction = animalReaction(animal, gesture, targets) ?: return false
        requests.add(Request(reaction, now))
        return true
    }

    fun drainPetting(): List<Petting> =
        buildList {
            while (true) add(hearts.poll() ?: break)
        }

    fun isNudging(animal: AnimalRef): Boolean = animal in nudges

    fun drainNudging(): List<AnimalRef> =
        buildList {
            while (true) add(nudging.poll() ?: break)
        }

    fun advance(
        engine: GameEngine,
        now: Instant,
    ) {
        while (true) {
            val request = requests.poll() ?: break
            val reaction = request.reaction
            if (engine.position(reaction.animal) == null) continue
            if (reaction.gesture == AnimalGesture.PET) {
                hearts.add(Petting(reaction.animal, request.at))
            } else {
                nudges[reaction.animal] = Nudge(random.nextInt(2..5), request.at.advancedBy(reaction.delay))
            }
        }
        for ((animal, nudge) in nudges.toMap()) {
            if (now < nudge.next) continue
            if (nudge.remaining <= 0 || engine.position(animal) == null) {
                nudges.remove(animal)
                continue
            }
            if (nudge.direction == null) nudging.add(animal)
            val direction = nudge.direction ?: engine.wanderDirection(animal, now, random)
            if (direction == null || engine.move(animal, direction) == null) {
                nudges.remove(animal)
            } else {
                nudges[animal] = Nudge(nudge.remaining - 1, now.advancedBy(0.5), direction)
            }
        }
    }
}

private val sceneGestures = WeakHashMap<GameSession, AnimalSceneGestures>()

val GameSession.animalGestures: AnimalSceneGestures
    get() = synchronized(sceneGestures) { sceneGestures.getOrPut(this) { AnimalSceneGestures() } }

/** One-line SheetHost hook. Sound and haptics can accompany this presentation request in the caller. */
fun GameSession.interactWithAnimal(
    animal: AnimalRef,
    interaction: AnimalInteraction,
) {
    val gesture = if (interaction == AnimalInteraction.PET) AnimalGesture.PET else AnimalGesture.NUDGE
    animalGestures.interact(animal, gesture, Instant.now())
}

/** A farm tap opens the options; only the matching implement collects ready produce instead. */
internal fun GameSession.interactWithAnimal(
    animal: AnimalRef,
    now: Instant,
): List<GameEvent> =
    act { engine ->
        if (engine.name(animal) == null) return@act emptyList()
        val point = engine.position(animal) ?: return@act emptyList()
        if (engine.state.mode == PlayMode.harvest &&
            engine.readyProduce(point, now)?.second == engine.state.harvestTool
        ) {
            flash(listOf(point), engine.useTool(point, now), now)
        } else {
            sheets.value = SheetTarget.Animal(animal)
            emptyList()
        }
    }

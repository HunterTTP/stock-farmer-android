@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.feedback

import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.rules.Rejection
import kotlin.math.pow

/** The kinds of sound the player can turn off or down on their own, apart from the music. */
enum class SoundCategory {
    /** The buttons, menus, shops, refusals and the fanfares. */
    `interface`,

    /** The farm at work: planting, building, destroying, cooking and fishing. */
    farm,
    animals,

    /** The farm's surroundings: the bird call and the midnight chime. */
    ambience,
}

/** The recordings of one sound: the files named `base.caf`, or `base_01.caf` and so on, in a folder of the bundled effects. */
data class SoundClips(
    val folder: String,
    val baseNames: List<String>,
) {
    constructor(folder: String, vararg baseNames: String) : this(folder, baseNames.toList())

    /** Whether a file, named without its extension, is one of these recordings. */
    fun includes(fileName: String): Boolean =
        baseNames.any { base ->
            if (!fileName.startsWith(base)) return@any false
            val suffix = fileName.drop(base.length)
            suffix.isEmpty() || (suffix.startsWith("_") && suffix.drop(1).all { it.isDigit() })
        }
}

/**
 * Every sound the game makes. Each is defined once, below: what it sounds like, which kind of sound it is and how loud it
 * sits in the mix.
 */
enum class SoundCue(
    val category: SoundCategory,
    val clips: SoundClips,
    /**
     * How far below its recording this sound is played, in decibels, so a mix is rebalanced here and not in the
     * recordings. A player volume can only turn a sound down, so this is never above zero.
     */
    val trimDB: Double,
) {
    roosterCrow(SoundCategory.animals, SoundClips("Animals", "Rooster"), 0.0),
    questComplete(SoundCategory.`interface`, SoundClips("Rewards", "quest_complete"), -1.5),
    cookDone(SoundCategory.farm, SoundClips("Ambience", "cook_done"), -3.5),
    fishingCast(SoundCategory.farm, SoundClips("Ambience", "fishing_cast"), -3.5),
    plant(SoundCategory.farm, SoundClips("Ambience", "dig"), -3.5),
    cropPickup(SoundCategory.farm, SoundClips("Pickups", "item"), -3.5),
    photoSaved(SoundCategory.`interface`, SoundClips("Feedback", "save_game"), -6.5),
    denied(SoundCategory.`interface`, SoundClips("Feedback", "denied"), -6.5),
    notEnoughMoney(SoundCategory.`interface`, SoundClips("Shop", "not_enough"), -6.5),
    locked(SoundCategory.`interface`, SoundClips("Feedback", "locked"), -6.5),
    levelUp(SoundCategory.`interface`, SoundClips("Rewards", "level_up"), -1.5),
    allCropsReady(SoundCategory.`interface`, SoundClips("Notifications", "notification_04"), -1.5),
    placeGround(SoundCategory.farm, SoundClips("Pickups", "item_drop"), -3.5),
    placeWater(SoundCategory.farm, SoundClips("Ambience", "bubble_float"), -3.5),
    built(SoundCategory.farm, SoundClips("Ambience", "craft_done"), -3.5),
    catTrill(SoundCategory.animals, SoundClips("Ambience", "pet_trill"), 0.0),
    henCluck(SoundCategory.animals, SoundClips("Animals", "Chicken", "ChickenAlternate"), 0.0),
    birdChirp(SoundCategory.ambience, SoundClips("Ambience", "tweet"), -5.5),
    nightPulse(SoundCategory.ambience, SoundClips("Ambience", "night_pulse"), -5.5),
    cowMoo(SoundCategory.animals, SoundClips("Animals", "Cow", "CowAlternate"), 0.0),
    dogBark(SoundCategory.animals, SoundClips("Animals", "Dog", "DogAlternate"), 0.0),
    pigOink(SoundCategory.animals, SoundClips("Animals", "Pig", "PigAlternate"), 0.0),
    sheepBleat(SoundCategory.animals, SoundClips("Animals", "pl_sheep_bleat"), 0.0),
    goatBleat(SoundCategory.animals, SoundClips("Animals", "Goat"), 0.0),
    rabbitSqueak(SoundCategory.animals, SoundClips("Animals", "Rabbit"), 0.0),
    donkeyBray(SoundCategory.animals, SoundClips("Animals", "Donkey"), 0.0),
    sell(SoundCategory.`interface`, SoundClips("Shop", "sell"), -4.5),
    destroy(SoundCategory.farm, SoundClips("Feedback", "delete"), -3.5),
    release(SoundCategory.farm, SoundClips("Pickups", "heart"), -3.5),
    reset(SoundCategory.`interface`, SoundClips("Feedback", "undo"), -6.5),
    selectItem(SoundCategory.`interface`, SoundClips("Buttons", "select"), -6.5),
    openPanel(SoundCategory.`interface`, SoundClips("Menus", "panel_slide_in"), -6.5),
    closePanel(SoundCategory.`interface`, SoundClips("Menus", "panel_slide_out"), -6.5),
    openPopup(SoundCategory.`interface`, SoundClips("Menus", "popup_open"), -6.5),
    cancel(SoundCategory.`interface`, SoundClips("Feedback", "cancel"), -6.5),
    toggleOn(SoundCategory.`interface`, SoundClips("Toggles", "toggle_on"), -6.5),
    ;

    /** Whether it is heard quieter the further the camera is zoomed out, or the further from the screen it is made: only the animals are. */
    val fadesWithDistance: Boolean get() = category == SoundCategory.animals

    /** The share of full volume the trim leaves, from 0 to 1. */
    val trimGain: Double get() = 10.0.pow(trimDB / 20)

    internal val isRefusal: Boolean get() = this == denied || this == notEnoughMoney || this == locked

    companion object {
        /** What a kind of livestock says. The goat, rabbit and donkey use stand-in recordings made from the others; the duck is silent. */
        fun voiceOfLivestock(speciesID: String): SoundCue? =
            when (speciesID) {
                LivestockSpecies.cow.id -> cowMoo
                LivestockSpecies.pig.id -> pigOink
                LivestockSpecies.sheep.id -> sheepBleat
                LivestockSpecies.goat.id -> goatBleat
                LivestockSpecies.rabbit.id -> rabbitSqueak
                LivestockSpecies.donkey.id -> donkeyBray
                else -> null
            }
    }
}

internal val Rejection.cue: SoundCue
    get() =
        when (this) {
            is Rejection.NeedMoney -> SoundCue.notEnoughMoney
            is Rejection.NeedLevel -> SoundCue.locked
            else -> SoundCue.denied
        }

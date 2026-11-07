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
     * How far below its recording this sound is played, in decibels. The recordings share one reference loudness, so
     * these trims are the mix: every button, menu, farm action and animal sits 8 dB down, the fanfares 4 dB above them.
     * The sounds a drag repeats tile by tile ring over one another, about 3 dB louder, so they sit that much lower. A few
     * recordings could only be mastered quieter than the reference without clipping (`dig`, `item`, `item_drop`,
     * `locked`), so they are trimmed less by what they fall short. A player volume can only turn a sound down, so this is
     * never above zero.
     */
    val trimDB: Double,
) {
    roosterCrow(SoundCategory.animals, SoundClips("Animals", "Rooster"), -8.0),
    questComplete(SoundCategory.`interface`, SoundClips("Rewards", "quest_complete"), -4.0),
    cookDone(SoundCategory.farm, SoundClips("Ambience", "cook_done"), -8.0),
    fishingCast(SoundCategory.farm, SoundClips("Ambience", "fishing_cast"), -8.0),
    plant(SoundCategory.farm, SoundClips("Ambience", "dig"), -8.0),
    cropPickup(SoundCategory.farm, SoundClips("Pickups", "item"), -10.0),
    photoSaved(SoundCategory.`interface`, SoundClips("Feedback", "save_game"), -8.0),
    denied(SoundCategory.`interface`, SoundClips("Feedback", "denied"), -8.0),
    notEnoughMoney(SoundCategory.`interface`, SoundClips("Shop", "not_enough"), -8.0),
    locked(SoundCategory.`interface`, SoundClips("Feedback", "locked"), -4.0),
    levelUp(SoundCategory.`interface`, SoundClips("Rewards", "level_up"), -4.0),
    allCropsReady(SoundCategory.`interface`, SoundClips("Notifications", "notification_04"), -4.0),
    placeGround(SoundCategory.farm, SoundClips("Pickups", "item_drop"), -9.0),
    placeWater(SoundCategory.farm, SoundClips("Ambience", "bubble_float"), -11.0),
    built(SoundCategory.farm, SoundClips("Ambience", "craft_done"), -8.0),
    catTrill(SoundCategory.animals, SoundClips("Ambience", "pet_trill"), -8.0),
    henCluck(SoundCategory.animals, SoundClips("Animals", "Chicken", "ChickenAlternate"), -8.0),
    birdChirp(SoundCategory.ambience, SoundClips("Ambience", "tweet"), -10.0),
    nightPulse(SoundCategory.ambience, SoundClips("Ambience", "night_pulse"), -10.0),
    cowMoo(SoundCategory.animals, SoundClips("Animals", "Cow", "CowAlternate"), -8.0),
    dogBark(SoundCategory.animals, SoundClips("Animals", "Dog", "DogAlternate"), -8.0),
    pigOink(SoundCategory.animals, SoundClips("Animals", "Pig", "PigAlternate"), -8.0),
    sheepBleat(SoundCategory.animals, SoundClips("Animals", "pl_sheep_bleat"), -8.0),
    goatBleat(SoundCategory.animals, SoundClips("Animals", "Goat"), -8.0),
    rabbitSqueak(SoundCategory.animals, SoundClips("Animals", "Rabbit"), -8.0),
    donkeyBray(SoundCategory.animals, SoundClips("Animals", "Donkey"), -8.0),
    sell(SoundCategory.`interface`, SoundClips("Shop", "sell"), -11.0),
    destroy(SoundCategory.farm, SoundClips("Feedback", "delete"), -11.0),
    release(SoundCategory.farm, SoundClips("Pickups", "heart"), -8.0),
    reset(SoundCategory.`interface`, SoundClips("Feedback", "undo"), -8.0),
    selectItem(SoundCategory.`interface`, SoundClips("Buttons", "select"), -8.0),
    openPanel(SoundCategory.`interface`, SoundClips("Menus", "panel_slide_in"), -8.0),
    closePanel(SoundCategory.`interface`, SoundClips("Menus", "panel_slide_out"), -8.0),
    openPopup(SoundCategory.`interface`, SoundClips("Menus", "popup_open"), -8.0),
    cancel(SoundCategory.`interface`, SoundClips("Feedback", "cancel"), -8.0),
    toggleOn(SoundCategory.`interface`, SoundClips("Toggles", "toggle_on"), -8.0),
    ;

    /** Whether it is heard quieter the further the camera is zoomed out, or the further from the screen it is made: only the animals are. */
    val fadesWithDistance: Boolean get() = category == SoundCategory.animals

    /** Whether the music is turned down while it plays: only the fanfares do. */
    val ducksMusic: Boolean get() = this == questComplete || this == levelUp || this == allCropsReady

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

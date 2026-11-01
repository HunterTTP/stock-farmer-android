package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import java.time.Instant

internal data class AnimalKey(
    val group: String,
    val home: GridPoint,
    val id: Int = 0,
)

internal data class AnimalSnapshot(
    val key: AnimalKey,
    val point: GridPoint,
    val look: AnimalSprites,
    val egg: Boolean = false,
) {
    companion object {
        fun all(
            state: GameState,
            now: Instant,
        ): List<AnimalSnapshot> =
            buildList {
                val eggs = state.eggs.map { it.coop to it.henID }.toSet()
                for ((home, pet) in state.pets) {
                    add(AnimalSnapshot(AnimalKey("pet", home), pet.position, AnimalSprites.pet(pet)))
                }
                for ((home, coop) in state.coops) {
                    for (chicken in coop.chickens) {
                        add(
                            AnimalSnapshot(
                                AnimalKey("chicken", home, chicken.id),
                                chicken.position,
                                AnimalSprites.chicken(chicken, now),
                                (home to chicken.id) in eggs,
                            ),
                        )
                    }
                }
                for ((home, herd) in state.herds) {
                    for (animal in herd.animals) {
                        add(
                            AnimalSnapshot(
                                AnimalKey("livestock", home, animal.id),
                                animal.position,
                                AnimalSprites.livestock(animal, now),
                            ),
                        )
                    }
                }
            }
    }
}

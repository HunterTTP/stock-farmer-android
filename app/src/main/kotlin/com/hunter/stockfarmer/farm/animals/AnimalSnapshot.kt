package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.favoriteCrop
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.isHungry
import java.time.Instant

internal data class AnimalKey(
    val group: String,
    val home: GridPoint,
    val id: Int = 0,
) {
    val ref: AnimalRef get() =
        when (group) {
            "pet" -> AnimalRef.Pet(home)
            "chicken" -> AnimalRef.Chicken(home, id)
            else -> AnimalRef.Livestock(home, id)
        }
}

internal data class AnimalSnapshot(
    val key: AnimalKey,
    val point: GridPoint,
    val look: AnimalSprites,
    val egg: Boolean = false,
    val badge: StorageItem? = if (egg) StorageItem.Egg else null,
    val entrance: AnimalEntrance? = null,
) {
    companion object {
        fun all(
            state: GameState,
            now: Instant,
        ): List<AnimalSnapshot> =
            buildList {
                val engine = GameEngine(state)
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
                                if ((home to chicken.id) in eggs) {
                                    StorageItem.Egg
                                } else {
                                    if (engine.isHungry(AnimalRef.Chicken(home, chicken.id), now)) {
                                        StorageItem.CropItem(Chicken.favoriteCrop)
                                    } else {
                                        null
                                    }
                                },
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
                                badge =
                                    animal.readyProduce(now)?.first ?: if (
                                        engine.isHungry(AnimalRef.Livestock(home, animal.id), now)
                                    ) {
                                        StorageItem.CropItem(animal.species.favoriteCrop ?: Crop.starter)
                                    } else {
                                        null
                                    },
                            ),
                        )
                    }
                }
            }.map { animal ->
                val home = state.building(animal.key.home)
                animal.copy(
                    entrance =
                        home?.takeIf { it.building.needsFrontTile }?.let {
                            AnimalEntrance(it.building.frontTile(it.origin), it.building.doorTile(it.origin))
                        },
                )
            }
    }
}

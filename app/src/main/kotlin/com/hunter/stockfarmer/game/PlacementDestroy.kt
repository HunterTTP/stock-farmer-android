package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.ObjectPlacement
import com.hunter.stockfarmer.core.rules.buildingPlacement
import com.hunter.stockfarmer.core.rules.cancelPlacement
import com.hunter.stockfarmer.core.rules.cropsToDestroy
import com.hunter.stockfarmer.core.rules.demolish
import com.hunter.stockfarmer.core.rules.destroyCrops
import com.hunter.stockfarmer.core.rules.destroyPlacedObject
import com.hunter.stockfarmer.core.rules.materialRefund
import com.hunter.stockfarmer.core.rules.refund
import java.time.Instant

/** Whether what is being placed is one already standing that is being moved, which can be destroyed instead. */
internal val GameEngine.canDestroyWhilePlacing: Boolean get() =
    buildingPlacement?.replacing != null || fruitPlacement?.replacing != null || objectPlacement?.isNew == false

/** What destroying a building gives back: money, and logs and stones when it took any. */
internal fun GameEngine.refundText(building: Building): String {
    val (logs, stones) = materialRefund(building)
    return listOfNotNull(
        refund(building).dollars,
        if (logs > 0) "$logs ${if (logs == 1) "log" else "logs"}" else null,
        if (stones > 0) "$stones ${if (stones == 1) "stone" else "stones"}" else null,
    ).joinToString(", ")
}

/** Asks whether to destroy the building, fruit tree, decoration or tree that is being moved, and says what comes back. */
fun GameSession.askToDestroyWhilePlacing() {
    val ask =
        act { engine ->
            engine.buildingPlacement?.replacing?.let { placed ->
                Confirmation(
                    "Destroy ${placed.building.name.lowercase()}?",
                    "You get back ${engine.refundText(placed.building)}",
                    "Destroy",
                ) { publish(act { it.demolish(placed) }) }
            } ?: engine.fruitPlacement?.let { tree ->
                val moved = tree.replacing ?: return@let null
                Confirmation("Destroy ${tree.crop.name.lowercase()}?", "No money will be returned.", "Destroy") {
                    publish(
                        act {
                            val targets = it.cropsToDestroy(moved.origin)
                            it.cancelPlacement()
                            it.destroyCrops(targets)
                        },
                    )
                }
            } ?: engine.objectPlacement?.let { moving ->
                val (name, message) =
                    when (val obj = moving.obj) {
                        is ObjectPlacement.Object.OfDecoration -> {
                            obj.placed.decoration.name to "You get back ${engine.refund(obj.placed.decoration).dollars}"
                        }

                        is ObjectPlacement.Object.OfTree -> {
                            obj.placed.tree.species.name to "No money will be returned."
                        }
                    }
                Confirmation("Destroy ${name.lowercase()}?", message, "Destroy") {
                    publish(act { it.destroyPlacedObject(Instant.now()) })
                }
            }
        }
    confirmations.value = ask
}

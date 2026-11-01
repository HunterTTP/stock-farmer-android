package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.PlayMode

data class LessonGuideState(
    val lesson: Lesson?,
    val toolTarget: PlayMode?,
    val needsScythe: Boolean,
    val message: String?,
    val pulses: Boolean,
) {
    val storageTarget: Boolean get() = lesson == Lesson.openStorage
    val isTeaching: Boolean get() = lesson != null

    companion object {
        fun from(state: GameState): LessonGuideState =
            from(Lesson.entries.firstOrNull { it !in state.lessonsLearned }, state.mode, state.harvestTool)

        fun from(
            lesson: Lesson?,
            mode: PlayMode,
            harvestTool: HarvestTool,
        ): LessonGuideState {
            val target =
                when (lesson) {
                    Lesson.plant -> PlayMode.plant.takeIf { mode != it }
                    Lesson.pickHarvest, Lesson.harvest -> PlayMode.harvest.takeIf { mode != it }
                    else -> null
                }
            val needsScythe =
                mode == PlayMode.harvest &&
                    (lesson == Lesson.pickHarvest || (lesson == Lesson.harvest && harvestTool != HarvestTool.scythe))
            val prompt =
                when {
                    target == PlayMode.harvest -> {
                        "Now get the Scythe" to true
                    }

                    needsScythe -> {
                        "Now get the Scythe" to false
                    }

                    target == PlayMode.plant -> {
                        "Tap the circle and pick Plant" to true
                    }

                    lesson == Lesson.plant -> {
                        "Drag from the circle over your farmland to plant ${Crop.starter.plantingName}" to true
                    }

                    lesson == Lesson.grow -> {
                        "Wait for your Wheat to finish growing" to false
                    }

                    lesson == Lesson.harvest -> {
                        "Drag the Scythe over the ripe Wheat to harvest it" to true
                    }

                    lesson == Lesson.openStorage -> {
                        "Tap the circle, then Storage, to see your Wheat" to true
                    }

                    else -> {
                        null
                    }
                }
            return LessonGuideState(lesson, target, needsScythe, prompt?.first, prompt?.second ?: false)
        }
    }
}

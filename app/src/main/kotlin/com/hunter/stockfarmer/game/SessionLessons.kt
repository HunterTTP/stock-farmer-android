package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.rules.currentLesson
import com.hunter.stockfarmer.core.rules.learn

/** Marks [lesson] done if it is the step being taught; true when it was. */
fun GameSession.learnLesson(lesson: Lesson): Boolean =
    act { engine ->
        if (engine.currentLesson == lesson) {
            engine.learn(lesson)
            true
        } else {
            false
        }
    }

package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.model.Changelog
import com.hunter.stockfarmer.core.model.ChangelogDay

internal object ScreenInformation {
    /** Markdown continuations keep the bundled reference readable at the repository's line width. */
    fun changelog(markdown: String): List<ChangelogDay> {
        val lines = mutableListOf<String>()
        markdown.lineSequence().forEach { line ->
            if (line.startsWith("  ") && lines.lastOrNull()?.startsWith("- ") == true) {
                lines[lines.lastIndex] += " " + line.trim()
            } else {
                lines += line
            }
        }
        return Changelog.parse(lines.joinToString("\n"))
    }
}

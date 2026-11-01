package com.hunter.stockfarmer.core.model

import java.util.regex.Pattern

private val nameCharacter = Pattern.compile("\\X")

/** Trim and limit Swift Characters (extended grapheme clusters), falling back for a blank result. */
fun String.cleanedName(
    maxLength: Int,
    fallback: String,
): String {
    require(maxLength >= 0)
    val trimmed = trim { Character.isSpaceChar(it) || it in '\t'..'\r' || it == '\u0085' }
    val matcher = nameCharacter.matcher(trimmed)
    var end = 0
    var count = 0
    while (count < maxLength && matcher.find()) {
        end = matcher.end()
        count++
    }
    return trimmed.substring(0, end).ifEmpty { fallback }
}

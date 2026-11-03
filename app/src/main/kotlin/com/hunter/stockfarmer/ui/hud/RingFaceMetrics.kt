package com.hunter.stockfarmer.ui.hud

import kotlin.math.max
import kotlin.math.sqrt

/**
 * How big the picture and name inside a ring button are. Everything is a fraction of the button's diameter (the
 * proportions iOS draws at 68pt). The type is sized in dp and handed back as the sp that draws that many dp at the
 * current system font scale, so a large font scale cannot push the name or picture out of the circle.
 */
data class RingFaceMetrics(
    val iconDp: Float,
    val glyphDp: Float,
    val fontDp: Float,
    val maxFontSp: Float,
    val minFontSp: Float,
    val gapDp: Float,
    val textWidthDp: Float,
) {
    companion object {
        private const val REFERENCE_DIAMETER = 68f
        private const val ICON = 36f
        private const val GLYPH = 30f
        private const val FONT = 12f
        private const val GAP = 1f
        private const val SIDE_PADDING = 2f
        private const val MIN_FONT_FRACTION = 0.6f
        private const val MIN_SAFE_FONT_SCALE = 0.1f

        /** A generous line height for the sans-serif face, so the stack is never measured too short. */
        const val LINE_HEIGHT_EM = 1.25f

        /** The metrics for a button [diameterDp] wide when the system font scale is [fontScale]. */
        fun of(
            diameterDp: Float,
            fontScale: Float,
        ): RingFaceMetrics {
            val unit = diameterDp / REFERENCE_DIAMETER
            val safeFontScale = fontScale.coerceAtLeast(MIN_SAFE_FONT_SCALE)
            val fontDp = FONT * unit
            val icon = ICON * unit
            val gap = GAP * unit
            val content = icon + gap + fontDp * LINE_HEIGHT_EM
            val radius = diameterDp / 2
            // The name is the widest thing at the stack's lower edge, where the circle is narrowest.
            val chord = 2 * sqrt(max(0f, radius * radius - (content / 2) * (content / 2)))
            return RingFaceMetrics(
                iconDp = icon,
                glyphDp = GLYPH * unit,
                fontDp = fontDp,
                maxFontSp = fontDp / safeFontScale,
                minFontSp = fontDp * MIN_FONT_FRACTION / safeFontScale,
                gapDp = gap,
                textWidthDp = chord - 2 * SIDE_PADDING * unit,
            )
        }
    }
}

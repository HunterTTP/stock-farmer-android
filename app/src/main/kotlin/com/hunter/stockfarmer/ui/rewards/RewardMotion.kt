package com.hunter.stockfarmer.ui.rewards

data class RewardPose(
    val position: RewardPoint,
    val scale: Float,
    val alpha: Float,
)

/** Positions use overlay-local logical pixels (dp); the tile projection is converted before launching. */
fun RewardFlight.pose(
    now: Double,
    tally: RewardPoint,
    banner: RewardPoint,
    target: RewardPoint?,
): RewardPose {
    val origin =
        when (val route = route) {
            is RewardRoute.FromTally -> tally.shifted(route.offset.x, route.offset.y)
            else -> if (startsAtBanner) banner else start
        }
    val risen = if (route == RewardRoute.Direct) origin.shifted(y = -36.0) else origin
    val elapsed = now - launchedAt - delay
    if (elapsed < 0) return RewardPose(origin, 0.6f, 0f)
    if (elapsed < riseDuration) {
        val progress = ease(elapsed / riseDuration, 0.0, 0.58)
        return RewardPose(origin.interpolate(risen, progress), (0.6 + 0.4 * progress).toFloat(), progress.toFloat())
    }
    val progress = ease(((elapsed - riseDuration - holdDuration) / flyDuration).coerceIn(0.0, 1.0), 0.42, 1.0)
    val destination = if (route == RewardRoute.ToTally) tally else target ?: risen
    val endScale = if (route == RewardRoute.ToTally) 0.7 else 0.35
    return RewardPose(
        risen.interpolate(destination, progress),
        (1 + (endScale - 1) * progress).toFloat(),
        (1 - progress).toFloat(),
    )
}

private fun RewardPoint.interpolate(
    other: RewardPoint,
    amount: Double,
): RewardPoint = RewardPoint(x + (other.x - x) * amount, y + (other.y - y) * amount)

// SwiftUI's easeIn/easeOut cubic curves have y control points 0 and 1.
private fun ease(
    progress: Double,
    x1: Double,
    x2: Double,
): Double {
    if (progress <= 0.0 || progress >= 1.0) return progress.coerceIn(0.0, 1.0)
    var low = 0.0
    var high = 1.0
    repeat(20) {
        val t = (low + high) / 2
        val x = 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t
        if (x < progress) low = t else high = t
    }
    val t = (low + high) / 2
    return 3 * (1 - t) * t * t + t * t * t
}

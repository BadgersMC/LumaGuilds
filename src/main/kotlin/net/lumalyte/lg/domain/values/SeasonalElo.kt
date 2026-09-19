package net.lumalyte.lg.domain.values

import kotlin.math.pow
import kotlin.math.roundToInt

data class SeasonalEloSettings(
    val kFactor: Int = 40,
    val upperDisplayRating: Int = 1600,
    val rematchWindowMillis: Long = 7L * 24L * 60L * 60L * 1000L,
) {
    init {
        require(kFactor > 0)
        require(upperDisplayRating > SeasonalElo.FLOOR_RATING)
        require(rematchWindowMillis > 0)
    }
}

object SeasonalElo {
    const val STARTING_RATING: Int = 1000
    const val FLOOR_RATING: Int = 1000
    fun expected(rating: Int, opponentRating: Int): Double =
        1.0 / (1.0 + 10.0.pow((opponentRating - rating).toDouble() / 400.0))

    fun calculate(
        firstRating: Int,
        secondRating: Int,
        firstScore: Double,
        secondScore: Double,
        kFactor: Int,
        floor: Int = FLOOR_RATING,
    ): Pair<Int, Int> {
        require(firstRating >= floor && secondRating >= floor)
        require(firstScore in 0.0..1.0 && secondScore in 0.0..1.0)
        require(kFactor > 0)
        val firstExpected = expected(firstRating, secondRating)
        val secondExpected = expected(secondRating, firstRating)
        val first = (firstRating + kFactor * (firstScore - firstExpected)).roundToInt().coerceAtLeast(floor)
        val second = (secondRating + kFactor * (secondScore - secondExpected)).roundToInt().coerceAtLeast(floor)
        return first to second
    }

    fun displayLevel(rating: Int, floor: Int = FLOOR_RATING, upper: Int = 1600): Int {
        require(upper > floor)
        if (rating <= floor) return 101
        if (rating >= upper) return 200
        val fraction = (rating - floor).toDouble() / (upper - floor).toDouble()
        return 101 + (fraction * 99.0).roundToInt().coerceIn(0, 99)
    }
}

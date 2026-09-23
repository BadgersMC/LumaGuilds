package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeasonalEloTest {
    @Test fun equalRatingsMoveSymmetrically() {
        val result = SeasonalElo.calculate(1000, 1000, 1.0, 0.0, 40)
        assertEquals(1020, result.first)
        assertEquals(1000, result.second)
    }

    @Test fun favoriteAndUnderdogUseOpponentWeighting() {
        val result = SeasonalElo.calculate(1600, 1000, 0.0, 1.0, 40)
        assertEquals(1561, result.first)
        assertEquals(1039, result.second)
    }

    @Test fun displayLevelMaps101To200AndCaps() {
        assertEquals(101, SeasonalElo.displayLevel(1000, 1000, 1600))
        assertEquals(200, SeasonalElo.displayLevel(1600, 1000, 1600))
        assertEquals(200, SeasonalElo.displayLevel(2200, 1000, 1600))
        assertEquals(151, SeasonalElo.displayLevel(1300, 1000, 1600))
    }
}

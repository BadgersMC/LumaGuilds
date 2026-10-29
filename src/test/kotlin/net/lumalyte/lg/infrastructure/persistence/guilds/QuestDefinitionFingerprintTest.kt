package net.lumalyte.lg.infrastructure.persistence.guilds

import org.junit.jupiter.api.Test
import net.lumalyte.lg.infrastructure.persistence.migrations.QuestSchema
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class QuestDefinitionFingerprintTest {
    private fun field(value: String) = value.toByteArray(Charsets.UTF_8)

    @Test fun `same ordered definition produces same fingerprint`() {
        assertEquals(QuestDefinitionFingerprint.of(listOf(field("bank"), null, field("5000"))),
            QuestDefinitionFingerprint.of(listOf(field("bank"), null, field("5000"))))
    }

    @Test fun `field changes, nulls and boundaries produce different fingerprints`() {
        val original = QuestDefinitionFingerprint.of(listOf(field("bank"), null, field("5000")))
        assertNotEquals(original, QuestDefinitionFingerprint.of(listOf(field("bank"), field(""), field("5000"))))
        assertNotEquals(original, QuestDefinitionFingerprint.of(listOf(field("bank"), null, field("5001"))))
        assertNotEquals(QuestDefinitionFingerprint.of(listOf(field("a"), field("bc"))),
            QuestDefinitionFingerprint.of(listOf(field("ab"), field("c"))))
    }
    @Test fun `snapshot tracks actual persisted definition and period`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            QuestSchema.create(connection, false)
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO weekly_quest_sets VALUES ('week', 100, 200, 1)")
                statement.execute("""
                    INSERT INTO weekly_quest_definitions (
                        week_id, quest_id, name_key, description_key, action, target_id, allowed_actions,
                        minimum_amount, maximum_amount, natural_dimensions, natural_biomes,
                        supported_conditions, provenance_policy, target_rarity, target_count, tier,
                        conditions, quest_order, experience_reward, item_rewards, leaderboard, leaderboard_payouts
                    ) VALUES ('week', 'bank', 'name', 'description', 'DEPOSIT_BANK', 'lumaguilds:bank/coins',
                        'DEPOSIT_BANK', 1, 10000, '', '', '', 'ANY', 'COMMON', 5000, 'COMMON',
                        '', 0, 500, '', 0, '')
                """.trimIndent())
            }
            val original = QuestDefinitionFingerprint.activeBankDefinitions(connection, 150).single()
            assertEquals("bank", original.questId)
            assertEquals(emptyList(), QuestDefinitionFingerprint.activeBankDefinitions(connection, 200))
            connection.createStatement().use { it.execute("UPDATE weekly_quest_definitions SET experience_reward=501") }
            val changed = QuestDefinitionFingerprint.activeBankDefinitions(connection, 150).single()
            assertNotEquals(original.fingerprint, changed.fingerprint)
        }
    }
}

package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class BedrockMenuNavigationTest {

    private lateinit var testPlayerId: String

    @BeforeEach
    fun setUp() {
        testPlayerId = UUID.randomUUID().toString()
    }

    @Test
    fun `test form state saving and restoration`() {
        val testState = mapOf("field1" to "value1", "field2" to 42)

        // Save state
        FormStateManager.saveState("$testPlayerId:test_key", testState)

        // Restore state
        val restoredState = FormStateManager.restoreState("$testPlayerId:test_key")

        assertEquals(testState, restoredState)
    }

    @Test
    fun `test form state expiration`() {
        val testState = mapOf("test" to "data")

        // Save state with short expiration
        FormStateManager.saveState("$testPlayerId:expire_test", testState, 0) // Expire immediately

        // Try to restore - should return empty map due to expiration
        val restoredState = FormStateManager.restoreState("$testPlayerId:expire_test")

        assertTrue(restoredState.isEmpty())
    }

    @Test
    fun `test workflow step saving and restoration`() {
        val step1Data = mapOf("name" to "Step 1", "completed" to true)
        val step2Data = mapOf("name" to "Step 2", "data" to "test")

        // Save workflow steps
        FormStateManager.saveState("$testPlayerId:test_menu:step1", step1Data)
        FormStateManager.saveState("$testPlayerId:test_menu:step2", step2Data)

        // Restore workflow
        val workflow = FormStateManager.restoreState("$testPlayerId:test_menu") as? Map<String, Map<String, Any?>> ?: emptyMap()

        assertEquals(2, workflow.size)
        assertEquals(step1Data, workflow["step1"])
        assertEquals(step2Data, workflow["step2"])
    }

    @Test
    fun `test workflow clearing`() {
        val stepData = mapOf("test" to "data")

        // Save workflow step
        FormStateManager.saveState("$testPlayerId:test_menu:test_step", stepData)

        // Verify it exists
        val workflow = FormStateManager.restoreState("$testPlayerId:test_menu")
        assertNotNull(workflow)

        // Clear workflow
        FormStateManager.clearState("$testPlayerId:test_menu")

        // Verify it's cleared
        val clearedWorkflow = FormStateManager.restoreState("$testPlayerId:test_menu")
        assertNull(clearedWorkflow)
    }

    @Test
    fun `test state manager player state isolation`() {
        val playerId1 = "player1"
        val playerId2 = "player2"

        val state1 = mapOf("player1" to "data1")
        val state2 = mapOf("player2" to "data2")

        // Save states for different players
        FormStateManager.saveState("$playerId1:test", state1)
        FormStateManager.saveState("$playerId2:test", state2)

        // Verify isolation
        assertEquals(state1, FormStateManager.restoreState("$playerId1:test"))
        assertEquals(state2, FormStateManager.restoreState("$playerId2:test"))

        // Clean up
        FormStateManager.clearPlayerStates(playerId1)
        FormStateManager.clearPlayerStates(playerId2)
    }

    @Test
    fun `test state manager timeout and cleanup`() {
        val testState = mapOf("test" to "data")

        // Save state with very short timeout
        FormStateManager.saveState("$testPlayerId:timeout_test", testState, 0)

        // Wait a bit for cleanup
        Thread.sleep(100)

        // Try to restore - should be cleaned up
        val restoredState = FormStateManager.restoreState("$testPlayerId:timeout_test")

        assertNull(restoredState)
    }

    @Test
    fun `test state manager with fallback`() {
        val fallbackData = mapOf("fallback" to "data")

        // Test fallback when state doesn't exist
        val result = FormStateManager.restoreStateWithFallback("$testPlayerId:nonexistent_key") {
            fallbackData
        }

        assertEquals(fallbackData, result)

        // Verify fallback was saved for future use
        val savedResult = FormStateManager.restoreState("$testPlayerId:nonexistent_key")
        assertEquals(fallbackData, savedResult)
    }

    @Test
    fun `test state key creation utility`() {
        val playerId = "test_player"
        val menuName = "TestMenu"
        val stateName = "test_state"

        val expectedKey = "$playerId:$menuName:$stateName"
        val actualKey = FormStateManager.createStateKey(playerId, menuName, stateName)

        assertEquals(expectedKey, actualKey)
    }

    @Test
    fun `test state updating and merging`() {
        val initialState = mapOf("key1" to "value1", "key2" to "value2")
        val newData = mapOf("key2" to "updated", "key3" to "new")

        // Save initial state
        FormStateManager.saveState("$testPlayerId:merge_test", initialState)

        // Update with new data
        FormStateManager.updateState("$testPlayerId:merge_test", newData)

        // Verify merge
        val finalState = FormStateManager.restoreState("$testPlayerId:merge_test")
        assertNotNull(finalState)
        assertEquals("value1", finalState["key1"])
        assertEquals("updated", finalState["key2"])
        assertEquals("new", finalState["key3"])

        // Clean up
        FormStateManager.clearState("$testPlayerId:merge_test")
    }

    @Test
    fun `test state metadata retrieval`() {
        val testState = mapOf("test1" to "data1", "test2" to "data2")

        // Save state
        FormStateManager.saveState("$testPlayerId:metadata_test", testState)

        // Get metadata
        val metadata = FormStateManager.getStateMetadata("$testPlayerId:metadata_test")

        assertNotNull(metadata)
        assertTrue(metadata!!["size"] as Int >= 2)
        assertTrue(metadata["expiresInMinutes"] as Long > 0)

        // Clean up
        FormStateManager.clearState("$testPlayerId:metadata_test")
    }

    @Test
    fun `test active state count monitoring`() {
        val initialCount = FormStateManager.getActiveStateCount()

        // Save a state
        FormStateManager.saveState("$testPlayerId:count_test", mapOf("test" to "data"))

        // Check count increased
        val newCount = FormStateManager.getActiveStateCount()
        assertTrue(newCount > initialCount)

        // Clean up
        FormStateManager.clearState("$testPlayerId:count_test")
    }

}

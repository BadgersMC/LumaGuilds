package net.lumalyte.lg.infrastructure.hytale.ui

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.ui.builder.EventData
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.lumalyte.lg.infrastructure.hytale.sounds.GuildSounds

/**
 * Test panel to verify UI system is working.
 * This is a simple "Hello World" UI with a button.
 */
class TestPanelPage(
    private val playerRef: PlayerRef
) : InteractiveCustomUIPage<TestPanelPage.TestEventData>(
    playerRef,
    CustomPageLifetime.CanDismiss,
    TestEventData.CODEC
) {

    companion object {
        const val LAYOUT = "LumaGuilds/TestPanel.ui"
    }

    override fun build(
        ref: Ref<EntityStore>,
        cmd: UICommandBuilder,
        evt: UIEventBuilder,
        store: Store<EntityStore>
    ) {
        // Load the UI layout
        cmd.append(LAYOUT)

        // Bind test button click
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TestButton",
            EventData().append("Action", "test_click"),
            false
        )

        // Bind close button
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CloseButton",
            EventData().append("Action", "close"),
            false
        )
    }

    override fun handleDataEvent(
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
        data: TestEventData
    ) {
        when (data.action) {
            "test_click" -> {
                // Play sound
                GuildSounds.playGuildCreated(playerRef)

                // Update status text
                val cmd = UICommandBuilder()
                cmd.set("#StatusText.Text", "Button clicked successfully! ✓")
                this.sendUpdate(cmd, false)
            }
            "close" -> {
                this.close()
            }
        }
    }

    class TestEventData {
        companion object {
            val CODEC: BuilderCodec<TestEventData> = BuilderCodec.builder(
                TestEventData::class.java
            ) { TestEventData() }
                .append(
                    KeyedCodec("Action", Codec.STRING),
                    { e, v -> e.action = v },
                    { e -> e.action }
                )
                .add()
                .build()
        }

        var action: String? = null
    }
}

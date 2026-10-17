package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

internal class GuildDialogNativeWidthTest {
    @Test
    fun itemDescriptionsReserveTheClientsInternalPaddingBeforeFittingCardGlyphs() {
        // 26.3 FocusableTextWidget uses 4px on each side of maxWidth, including
        // item descriptions. The card bitmap is 256px with a 1px font advance.
        assertTrue(GUILD_DIALOG_ITEM_DESCRIPTION_WIDTH - 8 >= 257,
            "Native description wrapping must not split the 257px background advance")
    }

    @Test
    fun nativeCloseLabelHasReadableFallbackWhenTheClientDoesNotDefineItsKey() {
        assertTrue(PlainTextComponentSerializer.plainText().serialize(GUILD_DIALOG_CLOSE_LABEL) == "Close")
    }
}

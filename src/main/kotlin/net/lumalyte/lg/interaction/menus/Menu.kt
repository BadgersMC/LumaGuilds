package net.lumalyte.lg.interaction.menus
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

interface Menu {
    fun open()
    fun passData(data: Any?) {
        // Default implementation does nothing
    }
}

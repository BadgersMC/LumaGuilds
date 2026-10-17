package net.lumalyte.lg.utils.inventoryframework

import com.github.stefvanschie.inventoryframework.gui.GuiComponent
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.util.MergedGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane as InventoryFrameworkPaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane as InventoryFrameworkStaticPane
import com.github.stefvanschie.inventoryframework.pane.util.Slot

/**
 * Preserves InventoryFramework 0.11's positioned-pane construction API while
 * delegating placement to InventoryFramework 0.12's Slot-based API.
 */
sealed interface PositionedPane {
    val origin: Slot
    val pane: Pane
}

class StaticPane : InventoryFrameworkStaticPane, PositionedPane {
    override val origin: Slot
    override val pane: Pane
        get() = this

    constructor(x: Int, y: Int, length: Int, height: Int) : super(length, height) {
        origin = Slot.fromXY(x, y)
    }

    constructor(x: Int, y: Int, length: Int, height: Int, priority: Pane.Priority) :
        super(length, height, priority) {
        origin = Slot.fromXY(x, y)
    }

    fun getItem(x: Int, y: Int): GuiItem? = super.getItem(Slot.fromXY(x, y))
}

class PaginatedPane : InventoryFrameworkPaginatedPane, PositionedPane {
    override val origin: Slot
    override val pane: Pane
        get() = this

    constructor(x: Int, y: Int, length: Int, height: Int) : super(length, height) {
        origin = Slot.fromXY(x, y)
    }

    constructor(x: Int, y: Int, length: Int, height: Int, priority: Pane.Priority) :
        super(length, height, priority) {
        origin = Slot.fromXY(x, y)
    }

    fun addPane(page: Int, pane: PositionedPane) {
        super.addPane(page, pane.origin, pane.pane)
    }

    fun addPage(pane: PositionedPane) {
        super.addPage(pane.origin, pane.pane)
    }
}

fun MergedGui.addPane(pane: PositionedPane) {
    addPane(pane.origin, pane.pane)
}

fun GuiComponent.addPane(pane: PositionedPane) {
    addPane(pane.origin, pane.pane)
}
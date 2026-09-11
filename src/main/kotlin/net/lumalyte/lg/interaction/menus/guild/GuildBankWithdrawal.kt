package net.lumalyte.lg.interaction.menus.guild

/** Withdrawal decisions shared by the menu's quick and custom amount actions. */
internal class GuildBankWithdrawal(private val currentBalance: () -> Long) {
    fun resolveAmount(requested: Int): Int =
        if (requested == -1) currentBalance().toInt() else requested

    fun execute(amount: Int, onEmpty: () -> Unit, transfer: (Int) -> Boolean): Boolean {
        if (amount <= 0) {
            onEmpty()
            return false
        }
        return transfer(amount)
    }
}

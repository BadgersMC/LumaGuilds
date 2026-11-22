package fixtures.invalid

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.paper.commands.annotations.Greedy
import net.badgersmc.nexus.paper.commands.annotations.Subcommand

@Suppress("UnusedParameter", "EmptyFunctionBlock")
@Command(name = "invalid-greedy")
class InvalidGreedyFixture {
    @Subcommand("send")
    fun send(@Arg("text") @Greedy text: String, @Arg("extra") extra: String) {}
}

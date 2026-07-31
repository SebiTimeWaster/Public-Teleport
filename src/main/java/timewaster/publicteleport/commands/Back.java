package timewaster.publicteleport.commands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.Teleports;

public class Back {
    private final Teleports teleports;

    public Back(CommandDispatcher<CommandSourceStack> dispatcher, Teleports teleports) {
        this.teleports = teleports;

        register(dispatcher);
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("back")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return teleports.teleportPlayer(player, "back", false);
            })));
    }
}

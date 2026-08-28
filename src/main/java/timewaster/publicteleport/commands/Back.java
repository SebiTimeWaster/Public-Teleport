package timewaster.publicteleport.commands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.Registrar;
import timewaster.publicteleport.Teleports;

/**
 * Defines the Back command, registered by {@link Registrar}.
 */
public class Back {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("back")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Teleports.teleportPlayer(player, "back", false);
            })));
    }
}

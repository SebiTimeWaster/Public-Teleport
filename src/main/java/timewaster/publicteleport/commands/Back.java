package timewaster.publicteleport.commands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.TeleportHandler;

public class Back {
    private TeleportHandler teleportHandler;

    public Back(CommandDispatcher<CommandSourceStack> dispatcher, TeleportHandler teleportHandler) {
        this.teleportHandler = teleportHandler;

        register(dispatcher);
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("back")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return teleportHandler.teleportPlayer(player, "back", false);
            })));
    }
}

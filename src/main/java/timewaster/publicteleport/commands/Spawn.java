package timewaster.publicteleport.commands;

import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.Teleports;
import timewaster.publicteleport.records.Teleport;

/**
 * Defines all Spawn commands, registered by {@link Registrar}.
 */
public class Spawn {
    private static final Predicate<CommandSourceStack> PERMISSIONS_OWNER = source -> source.permissions()
        .hasPermission(Permissions.COMMANDS_OWNER);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setspawn").requires(PERMISSIONS_OWNER)
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                Boolean isSaved = PublicTeleport.storage.setTeleport(player, Teleport.create(player, "spawn"), true);

                if (isSaved == null) {
                    return false;
                }

                Messages.sendMessage(player, "spawn_set", Messages.Type.SUCCESS);

                return true;
            })));

        dispatcher.register(Commands.literal("spawn")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Teleports.teleportPlayer(player, "spawn", true);
            })));
    }
}

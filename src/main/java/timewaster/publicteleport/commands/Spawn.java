package timewaster.publicteleport.commands;

import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.Storage;
import timewaster.publicteleport.Teleports;
import timewaster.publicteleport.records.Teleport;

public class Spawn {
    private static final Predicate<CommandSourceStack> PERMISSIONS_OWNER = source -> source.permissions()
        .hasPermission(Permissions.COMMANDS_OWNER);
    private final Storage storage;
    private final Teleports teleports;

    public Spawn(CommandDispatcher<CommandSourceStack> dispatcher, Storage storage, Teleports teleports) {
        this.storage = storage;
        this.teleports = teleports;

        register(dispatcher);
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setspawn").requires(PERMISSIONS_OWNER)
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                Boolean isSaved = storage.setTeleport(player, Teleport.create(player, "spawn"), true);

                if (isSaved == null) {
                    return false;
                }

                Messages.sendMessage(player, "spawn_set");

                return true;
            })));

        dispatcher.register(Commands.literal("spawn")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return teleports.teleportPlayer(player, "spawn", true);
            })));
    }
}

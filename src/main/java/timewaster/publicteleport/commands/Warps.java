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

public class Warps {
    private static final Predicate<CommandSourceStack> PERMISSIONS_OWNER = source -> source.permissions()
        .hasPermission(Permissions.COMMANDS_OWNER);
    private static final Registrar.SuggestionType typeNone = Registrar.SuggestionType.NONE;
    private static final Registrar.SuggestionType typeWarps = Registrar.SuggestionType.WARPS;
    private final Registrar registrar;
    private final Storage storage;
    private final Teleports teleports;

    public Warps(CommandDispatcher<CommandSourceStack> dispatcher, Registrar registrar, Storage storage,
        Teleports teleports) {
        this.registrar = registrar;
        this.storage = storage;
        this.teleports = teleports;

        register(dispatcher);
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setwarp").requires(PERMISSIONS_OWNER)
            .then(registrar.buildArgumentString("name", typeNone, (ServerPlayer player, String argValue) -> {
                storage.setTeleport(null, Teleport.create(player, argValue));
                Messages.sendMessage(player, "warp_set", argValue);

                return true;
            })));

        dispatcher.register(Commands.literal("delwarp").requires(PERMISSIONS_OWNER)
            .then(registrar.buildArgumentString("name", typeWarps, (ServerPlayer player, String argValue) -> {
                boolean success = storage.deleteTeleport(null, argValue);
                Messages.sendMessage(player, success ? "warp_deleted" : "warp_not_exist", argValue);

                return success;
            })));

        dispatcher.register(Commands.literal("warp")
            .then(registrar.buildArgumentString("name", typeWarps, (ServerPlayer player, String argValue) -> {
                return teleports.teleportPlayer(player, argValue, true);
            })));

        dispatcher.register(Commands.literal("warps")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Teleports.listTeleportNames(player, storage.getTeleportNames(null), true);
            })));
    }
}

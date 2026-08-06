package timewaster.publicteleport.commands;

import java.util.List;
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
                if (argValue.equals("spawn")) {
                    if (storage.getConfig().enableSpawn()) {
                        Messages.sendMessage(player, "warp_reserved_spawn_set", Messages.Type.ERROR, "/setspawn");
                    } else {
                        Messages.sendMessage(player, "warp_reserved_name", Messages.Type.ERROR);
                    }
                    return false;
                }

                Boolean isSaved = storage.setTeleport(player, Teleport.create(player, argValue), true);

                if (isSaved == null) {
                    return false;
                }

                Messages.sendMessage(player, "warp_set", Messages.Type.SUCCESS, argValue);

                return true;
            })));

        dispatcher.register(Commands.literal("delwarp").requires(PERMISSIONS_OWNER)
            .then(registrar.buildArgumentString("name", typeWarps, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("spawn")) {
                    Messages.sendMessage(player, "warp_no_exist", Messages.Type.ERROR, "spawn");
                    return false;
                }

                Boolean success = storage.deleteTeleport(player, argValue, true);

                if (success == null) {
                    return false;
                }

                if (success) {
                    Messages.sendMessage(player, "warp_deleted", Messages.Type.SUCCESS, argValue);
                } else {
                    Messages.sendMessage(player, "warp_no_exist", Messages.Type.ERROR, argValue);
                }

                return true;
            })));

        dispatcher.register(Commands.literal("warp")
            .then(registrar.buildArgumentString("name", typeWarps, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("spawn")) {
                    if (storage.getConfig().enableSpawn()) {
                        Messages.sendMessage(player, "warp_reserved_spawn_get", Messages.Type.ERROR, "/spawn");
                    } else {
                        Messages.sendMessage(player, "warp_no_exist", Messages.Type.ERROR, "spawn");
                    }
                    return false;
                }

                return teleports.teleportPlayer(player, argValue, true);
            })));

        dispatcher.register(Commands.literal("warps")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                List<String> names = storage.getTeleportNames(player, true);

                if (names == null) {
                    return false;
                }

                return Teleports.listTeleportNames(player, names, true);
            })));
    }
}

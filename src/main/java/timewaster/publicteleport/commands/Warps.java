package timewaster.publicteleport.commands;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;
import static timewaster.publicteleport.Registrar.SuggestionType.NONE;
import static timewaster.publicteleport.Registrar.SuggestionType.WARPS;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.Registrar;
import timewaster.publicteleport.TeleportSafety;
import timewaster.publicteleport.Teleports;
import timewaster.publicteleport.records.Teleport;

/**
 * Defines all Warp commands, registered by {@link Registrar}.
 */
public class Warps {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setwarp").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
            .then(Registrar.buildArgumentString("name", NONE, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("spawn")) {
                    if (PublicTeleport.storage.getConfig().enableSpawn()) {
                        Messages.sendMessage(player, "warp_reserved_spawn_set", WARNING,
                            "/setspawn");
                    } else {
                        Messages.sendMessage(player, "warp_reserved_name", WARNING);
                    }
                    return false;
                }

                Teleport target = Teleport.create(player, argValue);

                if (!TeleportSafety.isBlockTeleportable(player, target)) {
                    Messages.sendMessage(player, "teleport_unsafe_set", ERROR, "Warp");
                    return false;
                }

                Boolean isSaved = PublicTeleport.storage.setTeleport(player, target, true);

                if (isSaved == null) {
                    return false;
                }

                Messages.sendMessage(player, "warp_set", SUCCESS, argValue);

                return true;
            })));

        dispatcher.register(Commands.literal("delwarp").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
            .then(Registrar.buildArgumentString("name", WARPS, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("spawn")) {
                    Messages.sendMessage(player, "warp_no_exist", ERROR, "spawn");
                    return false;
                }

                Boolean success = PublicTeleport.storage.deleteTeleport(player, argValue, true);

                if (success == null) {
                    return false;
                }

                if (success) {
                    Messages.sendMessage(player, "warp_deleted", SUCCESS, argValue);
                } else {
                    Messages.sendMessage(player, "warp_no_exist", ERROR, argValue);
                }

                return true;
            })));

        dispatcher.register(Commands.literal("warp")
            .then(Registrar.buildArgumentString("name", WARPS, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("spawn")) {
                    if (PublicTeleport.storage.getConfig().enableSpawn()) {
                        Messages.sendMessage(player, "warp_reserved_spawn_get", WARNING, "/spawn");
                    } else {
                        Messages.sendMessage(player, "warp_no_exist", ERROR, "spawn");
                    }
                    return false;
                }

                return Teleports.teleportPlayer(player, argValue, true);
            })));

        dispatcher.register(Commands.literal("warps")
            .executes((context) -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                List<String> names = PublicTeleport.storage.getTeleportNames(player, true);

                if (names == null) {
                    return false;
                }

                Teleports.listTeleportNames(player, names, true);

                return true;
            })));
    }
}

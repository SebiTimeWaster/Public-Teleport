package timewaster.publicteleport.commands;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.Teleports;
import timewaster.publicteleport.TeleportSafety;
import timewaster.publicteleport.records.Teleport;

/**
 * Defines all Home commands, registered by {@link Registrar}.
 */
public class Homes {
    private static final Registrar.SuggestionType typeNone = Registrar.SuggestionType.NONE;
    private static final Registrar.SuggestionType typeHomes = Registrar.SuggestionType.HOMES;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sethome")
            .then(Registrar.buildArgumentString("name", typeNone, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("back")) {
                    Messages.sendMessage(player, "home_reserved_name", Messages.Type.WARNING);
                    return false;
                }

                Teleport target = Teleport.create(player, argValue);

                if (!TeleportSafety.isBlockTeleportable(player, target)) {
                    Messages.sendMessage(player, "teleport_unsafe_set", Messages.Type.ERROR, "Home");
                    return false;
                }

                Boolean isSaved = PublicTeleport.storage.setTeleport(player, target, false);

                if (isSaved == null) {
                    return false;
                }

                if (isSaved) {
                    Messages.sendMessage(player, "home_set_named", Messages.Type.SUCCESS, argValue);
                } else {
                    Messages.sendMessage(player, "home_set_max_reached", Messages.Type.WARNING,
                        PublicTeleport.storage.getConfig().maxHomes());
                }

                return true;
            }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                Teleport target = Teleport.create(player, "home");

                if (!TeleportSafety.isBlockTeleportable(player, target)) {
                    Messages.sendMessage(player, "teleport_unsafe_set", Messages.Type.ERROR, "Home");
                    return false;
                }

                Boolean isSaved = PublicTeleport.storage.setTeleport(player, target, false);

                if (isSaved == null) {
                    return false;
                }

                if (isSaved) {
                    Messages.sendMessage(player, "home_set", Messages.Type.SUCCESS);
                } else {
                    Messages.sendMessage(player, "home_set_max_reached", Messages.Type.WARNING,
                        PublicTeleport.storage.getConfig().maxHomes());
                }

                return true;
            })));

        dispatcher.register(Commands.literal("delhome")
            .then(Registrar.buildArgumentString("name", typeHomes, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("back")) {
                    Messages.sendMessage(player, "home_no_exist", Messages.Type.ERROR, argValue);
                    return false;
                }

                Boolean success = PublicTeleport.storage.deleteTeleport(player, argValue, false);

                if (success == null) {
                    return false;
                }

                if (success) {
                    Messages.sendMessage(player, "home_deleted", Messages.Type.SUCCESS, argValue);
                } else {
                    Messages.sendMessage(player, "home_no_exist", Messages.Type.ERROR, argValue);
                }

                return true;
            })));

        dispatcher.register(Commands.literal("home")
            .then(Registrar.buildArgumentString("name", typeHomes, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("back")) {
                    if (PublicTeleport.storage.getConfig().enableBack()) {
                        Messages.sendMessage(player, "home_reserved_name_get", Messages.Type.WARNING, "/back");
                    } else {
                        Messages.sendMessage(player, "home_no_exist", Messages.Type.ERROR, argValue);
                    }
                    return false;
                }

                return Teleports.teleportPlayer(player, argValue, false);
            }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Teleports.teleportPlayer(player, "home", false);
            })));

        dispatcher.register(Commands.literal("homes")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                List<String> names = PublicTeleport.storage.getTeleportNames(player, false);

                if (names == null) {
                    return false;
                }

                Teleports.listTeleportNames(player, names, false);

                return true;
            })));
    }
}

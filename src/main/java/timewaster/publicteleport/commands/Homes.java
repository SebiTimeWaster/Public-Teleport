package timewaster.publicteleport.commands;

import static timewaster.publicteleport.Messages.MessageType.ERROR;
import static timewaster.publicteleport.Messages.MessageType.SUCCESS;
import static timewaster.publicteleport.Messages.MessageType.WARNING;
import static timewaster.publicteleport.Registrar.SuggestionType.HOMES;
import static timewaster.publicteleport.Registrar.SuggestionType.NONE;

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
 * Defines all Home commands, registered by {@link Registrar}.
 */
public final class Homes {
    private Homes() {
    }

    private static boolean setHome(ServerPlayer player, String name) {
        Teleport target = Teleport.create(player, name);

        if (!TeleportSafety.isBlockTeleportable(player, target)) {
            Messages.sendMessage(player, "teleport_unsafe_set", ERROR, "Home");
            return false;
        }

        Boolean isSaved = PublicTeleport.storage.setTeleport(player, target, false);

        if (isSaved == null) {
            return false;
        }

        if (isSaved) {
            Messages.sendMessage(player, "home_set" + ("home".equals(name) ? "" : "_named"), SUCCESS, name);
        } else {
            Messages.sendMessage(player, "home_set_max_reached", WARNING,
                PublicTeleport.storage.getConfig().maxHomes());
        }

        return true;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sethome")
            .then(Registrar.buildArgumentString("name", NONE, (ServerPlayer player, String argValue) -> {
                if ("back".equals(argValue)) {
                    Messages.sendMessage(player, "home_reserved_name", WARNING);
                    return false;
                }

                return setHome(player, argValue);
            }))
            .executes((context) -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return setHome(player, "home");
            })));

        dispatcher.register(Commands.literal("delhome")
            .then(Registrar.buildArgumentString("name", HOMES, (ServerPlayer player, String argValue) -> {
                if ("back".equals(argValue)) {
                    Messages.sendMessage(player, "home_no_exist", ERROR, argValue);
                    return false;
                }

                Boolean success = PublicTeleport.storage.deleteTeleport(player, argValue, false);

                if (success == null) {
                    return false;
                }

                if (success) {
                    Messages.sendMessage(player, "home_deleted", SUCCESS, argValue);
                } else {
                    Messages.sendMessage(player, "home_no_exist", ERROR, argValue);
                }

                return true;
            })));

        dispatcher.register(Commands.literal("home")
            .then(Registrar.buildArgumentString("name", HOMES, (ServerPlayer player, String argValue) -> {
                if ("back".equals(argValue)) {
                    if (PublicTeleport.storage.getConfig().enableBack()) {
                        Messages.sendMessage(player, "home_reserved_name_get", WARNING, "/back");
                    } else {
                        Messages.sendMessage(player, "home_no_exist", ERROR, argValue);
                    }
                    return false;
                }

                return Teleports.teleportPlayer(player, argValue, false);
            }))
            .executes((context) -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Teleports.teleportPlayer(player, "home", false);
            })));

        dispatcher.register(Commands.literal("homes")
            .executes((context) -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                List<String> names = PublicTeleport.storage.getTeleportNames(player, false);

                if (names == null) {
                    return false;
                }

                Teleports.listTeleportNames(player, names, false);

                return true;
            })));
    }
}

package timewaster.publicteleport.commands;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.Storage;
import timewaster.publicteleport.Teleports;
import timewaster.publicteleport.records.Teleport;

public class Homes {
    private static final Registrar.SuggestionType typeNone = Registrar.SuggestionType.NONE;
    private static final Registrar.SuggestionType typeHomes = Registrar.SuggestionType.HOMES;
    private final Registrar registrar;
    private final Storage storage;
    private final Teleports teleports;

    public Homes(CommandDispatcher<CommandSourceStack> dispatcher, Registrar registrar, Storage storage,
        Teleports teleports) {
        this.registrar = registrar;
        this.storage = storage;
        this.teleports = teleports;

        register(dispatcher);
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sethome")
            .then(registrar.buildArgumentString("name", typeNone, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("back")) {
                    Messages.sendMessage(player, "home_reserved_name", Messages.Type.ERROR);
                    return false;
                }

                Boolean isSaved = storage.setTeleport(player, Teleport.create(player, argValue), false);

                if (isSaved == null) {
                    return false;
                }

                if (isSaved) {
                    Messages.sendMessage(player, "home_set_named", Messages.Type.SUCCESS, argValue);
                } else {
                    Messages.sendMessage(player, "home_set_max_reached", Messages.Type.ERROR,
                        storage.getConfig().maxHomes());
                }

                return true;
            }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                Boolean isSaved = storage.setTeleport(player, Teleport.create(player, "home"), false);

                if (isSaved == null) {
                    return false;
                }

                if (isSaved) {
                    Messages.sendMessage(player, "home_set", Messages.Type.SUCCESS);
                } else {
                    Messages.sendMessage(player, "home_set_max_reached", Messages.Type.ERROR,
                        storage.getConfig().maxHomes());
                }

                return true;
            })));

        dispatcher.register(Commands.literal("delhome")
            .then(registrar.buildArgumentString("name", typeHomes, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("back")) {
                    Messages.sendMessage(player, "home_no_exist", Messages.Type.ERROR, argValue);
                    return false;
                }

                Boolean success = storage.deleteTeleport(player, argValue, false);

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
            .then(registrar.buildArgumentString("name", typeHomes, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("back")) {
                    if (storage.getConfig().enableBack()) {
                        Messages.sendMessage(player, "home_reserved_name_get", Messages.Type.ERROR, "/back");
                    } else {
                        Messages.sendMessage(player, "home_no_exist", Messages.Type.ERROR, argValue);
                    }
                    return false;
                }

                return teleports.teleportPlayer(player, argValue, false);
            }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return teleports.teleportPlayer(player, "home", false);
            })));

        dispatcher.register(Commands.literal("homes")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                List<String> names = storage.getTeleportNames(player, false);

                if (names == null) {
                    return false;
                }

                return Teleports.listTeleportNames(player, names, false);
            })));
    }
}

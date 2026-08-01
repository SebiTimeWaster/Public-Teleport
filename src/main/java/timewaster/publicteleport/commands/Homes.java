package timewaster.publicteleport.commands;

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
                    Messages.sendMessage(player, "home_reserved_name");
                    return false;
                }

                if (!storage.setTeleport(player.getUUID(), Teleport.create(player, argValue))) {
                    Messages.sendMessage(player, "home_set_max_reached", storage.getConfig().maxHomes());

                    return false;
                } else {
                    Messages.sendMessage(player, "home_set_named", argValue);

                    return true;
                }
            }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                if (!storage.setTeleport(player.getUUID(), Teleport.create(player, "home"))) {
                    Messages.sendMessage(player, "home_set_max_reached", storage.getConfig().maxHomes());

                    return false;
                } else {
                    Messages.sendMessage(player, "home_set");

                    return true;
                }
            })));

        dispatcher.register(Commands.literal("delhome")
            .then(registrar.buildArgumentString("name", typeHomes, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("back")) {
                    Messages.sendMessage(player, "home_reserved_name");
                    return false;
                }

                boolean success = storage.deleteTeleport(player.getUUID(), argValue);
                Messages.sendMessage(player, success ? "home_deleted" : "home_no_exist", argValue);

                return success;
            })));

        dispatcher.register(Commands.literal("home")
            .then(registrar.buildArgumentString("name", typeHomes, (ServerPlayer player, String argValue) -> {
                if (argValue.equals("back")) {
                    Messages.sendMessage(player, "home_reserved_name");
                    return false;
                }

                return teleports.teleportPlayer(player, argValue, false);
            }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return teleports.teleportPlayer(player, "home", false);
            })));

        dispatcher.register(Commands.literal("homes")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return Teleports.listTeleportNames(player, storage.getTeleportNames(player.getUUID()), false);
            })));
    }
}

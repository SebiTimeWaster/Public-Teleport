package timewaster.publicteleport.commands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.FileHandler;
import timewaster.publicteleport.MessageHandler;
import timewaster.publicteleport.TeleportHandler;
import timewaster.publicteleport.records.Teleport;

public class Homes {
    private final Registrar registrar;
    private FileHandler fileHandler;
    private TeleportHandler teleportHandler;

    public Homes(CommandDispatcher<CommandSourceStack> dispatcher, Registrar registrar, FileHandler fileHandler,
        TeleportHandler teleportHandler) {
        this.registrar = registrar;
        this.fileHandler = fileHandler;
        this.teleportHandler = teleportHandler;

        register(dispatcher);
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sethome")
            .then(registrar.buildArgumentString("name", Registrar.SuggestionType.NONE,
                (ServerPlayer player, String argValue) -> {
                    if (argValue.equals("back")) {
                        MessageHandler.sendMessage(player, "reserved_name");
                        return false;
                    }

                    fileHandler.setTeleport(player.getUUID(), Teleport.create(player, argValue));
                    MessageHandler.sendMessage(player, "named_home_set", argValue);

                    return true;
                }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                fileHandler.setTeleport(player.getUUID(), Teleport.create(player, "home"));
                MessageHandler.sendMessage(player, "home_set");

                return true;
            })));

        dispatcher.register(Commands.literal("delhome")
            .then(registrar.buildArgumentString("name", Registrar.SuggestionType.HOMES,
                (ServerPlayer player, String argValue) -> {
                    if (argValue.equals("back")) {
                        MessageHandler.sendMessage(player, "reserved_name");
                        return false;
                    }

                    boolean success = fileHandler.deleteTeleport(player.getUUID(), argValue);
                    MessageHandler.sendMessage(player, success ? "home_deleted" : "home_not_exist", argValue);

                    return success;
                })));

        dispatcher.register(Commands.literal("home")
            .then(registrar.buildArgumentString("name", Registrar.SuggestionType.HOMES,
                (ServerPlayer player, String argValue) -> {
                    if (argValue.equals("back")) {
                        MessageHandler.sendMessage(player, "reserved_name");
                        return false;
                    }

                    return teleportHandler.teleportPlayer(player, argValue, false);
                }))
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return teleportHandler.teleportPlayer(player, "home", false);
            })));

        dispatcher.register(Commands.literal("homes")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return MessageHandler.listTeleportNames(player, fileHandler.getTeleportNames(player.getUUID()), false);
            })));
    }
}

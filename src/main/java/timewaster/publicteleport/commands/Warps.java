package timewaster.publicteleport.commands;

import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import timewaster.publicteleport.FileHandler;
import timewaster.publicteleport.MessageHandler;
import timewaster.publicteleport.TeleportHandler;
import timewaster.publicteleport.records.Teleport;

public class Warps {
    private static final Predicate<CommandSourceStack> PERMISSIONS_OWNER = source -> source.permissions()
        .hasPermission(Permissions.COMMANDS_OWNER);
    private final Registrar registrar;
    private FileHandler fileHandler;
    private TeleportHandler teleportHandler;

    public Warps(CommandDispatcher<CommandSourceStack> dispatcher, Registrar registrar, FileHandler fileHandler,
        TeleportHandler teleportHandler) {
        this.registrar = registrar;
        this.fileHandler = fileHandler;
        this.teleportHandler = teleportHandler;

        register(dispatcher);
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setwarp").requires(PERMISSIONS_OWNER)
            .then(registrar.buildArgumentString("name", Registrar.SuggestionType.NONE,
                (ServerPlayer player, String argValue) -> {
                    fileHandler.setTeleport(null, Teleport.create(player, argValue));
                    MessageHandler.sendMessage(player, "warp_set", argValue);

                    return true;
                })));

        dispatcher.register(Commands.literal("delwarp").requires(PERMISSIONS_OWNER)
            .then(registrar.buildArgumentString("name", Registrar.SuggestionType.WARPS,
                (ServerPlayer player, String argValue) -> {
                    boolean success = fileHandler.deleteTeleport(null, argValue);
                    MessageHandler.sendMessage(player, success ? "warp_deleted" : "warp_not_exist", argValue);

                    return success;
                })));

        dispatcher.register(Commands.literal("warp")
            .then(registrar.buildArgumentString("name", Registrar.SuggestionType.WARPS,
                (ServerPlayer player, String argValue) -> {
                    return teleportHandler.teleportPlayer(player, argValue, true);
                })));

        dispatcher.register(Commands.literal("warps")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return MessageHandler.listTeleportNames(player, fileHandler.getTeleportNames(null), true);
            })));
    }
}

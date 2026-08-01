package timewaster.publicteleport.commands;

import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData.RespawnData;
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
                ServerLevel world = player.level();
                RespawnData spawn = RespawnData.of(player.level().dimension(), player.blockPosition(), 0, 0);
                MinecraftServer server = world.getServer();

                storage.setTeleport(null, Teleport.create(player, "spawn"));
                world.setRespawnData(spawn);
                server.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, server);
                Messages.sendMessage(player, "spawn_set");

                return true;
            })));

        dispatcher.register(Commands.literal("spawn")
            .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {
                return teleports.teleportPlayer(player, "spawn", true);
            })));
    }
}

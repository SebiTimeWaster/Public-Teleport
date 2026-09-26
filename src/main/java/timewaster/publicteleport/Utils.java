package timewaster.publicteleport;

import java.util.List;
import java.util.Objects;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import timewaster.publicteleport.records.Teleport;

public final class Utils {
    private Utils() {
    }

    /**
     * Get the player list from a given {@link Level}
     *
     * @param level the level to get the list from
     * @return the fetched list
     */
    public static List<ServerPlayer> getPlayersByLevel(Level level) {
        return level.getServer().getPlayerList().getPlayers();
    }

    /**
     * Get the dimension name from a given {@link Level}
     *
     * @param level the level to get the name from
     * @return the fetched name
     */
    public static String getDimensionNameByLevel(Level level) {
        return level.dimension().identifier().toString();
    }

    /**
     * Get the server from a given {@link player}
     *
     * @param player the player to get the server from
     * @return the fetched server
     */
    public static MinecraftServer getServerByPlayer(ServerPlayer player) {
        return player.level().getServer();
    }

    /**
     * Gets a level from a {@link Teleport} target dimension identifier.
     *
     * @param player    the player to be teleported
     * @param dimension the dimension name
     * @return the level matching the dimension identifier
     */
    public static ServerLevel getLevelbyDimension(ServerPlayer player, String dimension) {
        Identifier dimId = Identifier.parse(Objects.requireNonNull(dimension));
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);

        return getServerByPlayer(player).getLevel(dimKey);
    }

    /**
     * Gets a player from a {@link CommandContext}.
     *
     * @param context the context to be used
     * @return the fetched player
     */
    public static ServerPlayer getPlayerByContext(CommandContext<CommandSourceStack> context) {
        return context.getSource().getPlayer();
    }
}

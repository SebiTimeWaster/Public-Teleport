package timewaster.publicteleport;

import java.util.List;
import java.util.Objects;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

    /**
     * Gets the bounding box of the player, or optionally of the entity they ride
     * together with all its passengers.
     *
     * @param player   the player to be checked
     * @param withRide if the ride and its passengers are included
     * @return the calculated bounding box
     */
    public static AABB getPlayerBoundingBox(ServerPlayer player, boolean withRide) {
        if (!withRide) {
            return player.getBoundingBox();
        }
        Entity entity = player.getRootVehicle();
        AABB boundingBox = entity.getBoundingBox();

        for (Entity passenger : entity.getIndirectPassengers()) {
            boundingBox = boundingBox.minmax(passenger.getBoundingBox());
        }

        return boundingBox.setMinY(entity.getBoundingBox().minY);
    }

    /**
     * Moves the bounding box to the specified position.
     *
     * @param boundingBox the given bounding box
     * @param blockPos    the position the bounding box is moved to
     * @return the moved bounding box
     */
    public static AABB moveBoundingBoxTo(AABB boundingBox, BlockPos blockPos) {
        Vec3 centerPos = boundingBox.getBottomCenter();

        return boundingBox.move(
            blockPos.getX() + 0.5 - centerPos.x, // middle point of block
            blockPos.getY() + 0.01 - centerPos.y, // slightly above ground
            blockPos.getZ() + 0.5 - centerPos.z);
    }

    /**
     * Checks if a player is further away from a block position than the
     * specified clearances, or in another dimension.
     *
     * @param player      the player whose position to check
     * @param blockPos    the position to check
     * @param dimension   the dimension of the position
     * @param clearanceXZ the minimum clearance in the X and Z directions needed
     * @param clearanceY  the minimum clearance in the Y direction needed
     * @return {@code true} if the player clears the position
     */
    public static boolean doesPlayerClearTargetBlock(ServerPlayer player, BlockPos blockPos, String dimension, double clearanceXZ, double clearanceY) {
        return !Utils.getDimensionNameByLevel(player.level()).equals(dimension)
            || Math.abs(player.getX() - (blockPos.getX() + 0.5)) > clearanceXZ // middle point of block
            || Math.abs(player.getY() - (blockPos.getY() + 0.01)) > clearanceY // slightly above ground
            || Math.abs(player.getZ() - (blockPos.getZ() + 0.5)) > clearanceXZ;
    }
}

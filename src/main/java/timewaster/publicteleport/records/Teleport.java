package timewaster.publicteleport.records;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.TeleportSafety;

/**
 * A single named teleport destination, used for both Warps and Homes.
 *
 * @param name      the name of the teleport
 * @param x         block x-coordinate
 * @param y         block y-coordinate
 * @param z         block z-coordinate
 * @param yaw       facing yaw in degrees, or {@code null} if not set
 * @param pitch     facing pitch in degrees, or {@code null} if not set
 * @param dimension identifier of the dimension/world this teleport belongs to
 */
public final record Teleport(
    String name,
    int x,
    int y,
    int z,
    Float yaw,
    Float pitch,
    String dimension) {

    /**
     * Creates a teleport destination from the players current position.
     *
     * @param player the player whose position is used
     * @param name   the name of the teleport destination
     * @return the teleport destination created
     */
    public static final Teleport create(ServerPlayer player, String name) {
        BlockPos playerPos = TeleportSafety.getPlayerBlockPos(player);

        return new Teleport(
            name,
            playerPos.getX(),
            playerPos.getY(),
            playerPos.getZ(),
            (Float) player.getYRot(),
            (Float) player.getXRot(),
            TeleportSafety.getDimensionName(player.level()));
    }
}

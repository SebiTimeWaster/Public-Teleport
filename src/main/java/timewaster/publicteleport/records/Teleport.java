package timewaster.publicteleport.records;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import timewaster.publicteleport.Utils;

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
public record Teleport(
    String name,
    int x,
    int y,
    int z,
    Float yaw,
    Float pitch,
    String dimension) {

    /**
     * Creates a teleport destination from the players current position, or from
     * the position of the vehicle the player rides (which stands on the ground).
     *
     * @param player the player whose position is used
     * @param name   the name of the teleport destination
     * @return the teleport destination created
     */
    public static Teleport create(ServerPlayer player, String name) {
        Entity entity = player.getRootVehicle();
        double entityY = entity.getY();
        // prevent weird scaffolding Y = x.00032, a carpet/rail is 0.0625 high
        double realY = entityY - Math.floor(entityY) > 0.06 ? Math.ceil(entityY) : Math.floor(entityY);

        return new Teleport(
            name,
            (int) Math.floor(entity.getX()),
            (int) realY,
            (int) Math.floor(entity.getZ()),
            (Float) player.getYRot(),
            (Float) player.getXRot(),
            Utils.getDimensionNameByLevel(player.level()));
    }
}

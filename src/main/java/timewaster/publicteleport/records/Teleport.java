package timewaster.publicteleport.records;

import net.minecraft.server.level.ServerPlayer;

/**
 * A single named teleport destination (used for both warps and homes).
 *
 * @param name      the unique (per-list) name identifying this teleport
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
        return new Teleport(
            name,
            (int) Math.floor(player.getX()),
            (int) Math.ceil(player.getY()),
            (int) Math.floor(player.getZ()),
            (Float) player.getYRot(),
            (Float) player.getXRot(),
            player.level().dimension().identifier().toString());
    }
}

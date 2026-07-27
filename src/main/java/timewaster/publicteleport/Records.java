package timewaster.publicteleport;

import java.util.UUID;

import org.jspecify.annotations.NonNull;

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
record Teleport(@NonNull String name, int x, int y, int z, Float yaw, Float pitch, @NonNull String dimension) {
    /**
     * Creates a teleport destination from the players current position.
     *
     * @param player the player whose position is used
     * @param name   the name of the teleport destination
     * @return the teleport destination created
     */
    static Teleport create(ServerPlayer player, @NonNull String name) {
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

/**
 * Mod-wide configuration options.
 *
 * @param maxHomes    maximum number of homes a single player may set
 * @param enableSpawn whether the spawn features are enabled
 * @param enableWarps whether warps are enabled
 * @param enableHomes whether homes are enabled
 * @param enableBack  whether the "back" (return to previous location) feature
 *                    is enabled
 * @param enableTpa   whether player-to-player teleport requests (tpa) are
 *                    enabled
 */
record Config(int maxHomes, boolean enableSpawn, boolean enableWarps, boolean enableHomes,
        boolean enableBack, boolean enableTpa) {

}

record Request(UUID sender, UUID receiver, boolean here, long expiry) {

}

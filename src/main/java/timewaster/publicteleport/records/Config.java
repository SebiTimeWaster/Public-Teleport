package timewaster.publicteleport.records;

/**
 * Mod-wide configuration options.
 *
 * @param maxHomes    maximum number of homes a single player may set
 * @param enableSpawn whether the spawn features are enabled
 * @param enableWarps whether warps are enabled
 * @param enableHomes whether homes are enabled
 * @param enableBack  whether the "back" (return to previous location) feature
 *                        is enabled
 * @param enableTpa   whether player-to-player teleport requests (tpa) are
 *                        enabled
 */
public final record Config(
    int maxHomes,
    int requestTimeout,
    boolean enableSpawn,
    boolean enableWarps,
    boolean enableHomes,
    boolean enableBack,
    boolean enableTpa) {
}

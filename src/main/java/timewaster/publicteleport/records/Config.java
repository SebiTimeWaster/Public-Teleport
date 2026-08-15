package timewaster.publicteleport.records;

/**
 * Mod-wide configuration options.
 *
 * @param defaultlanguage The default language to use
 * @param maxHomes        maximum number of homes a single player may set
 * @param enableSpawn     whether the spawn features are enabled
 * @param enableWarps     whether the warps features are enabled
 * @param enableHomes     whether the homes features are enabled
 * @param enableBack      whether the back features are enabled
 * @param enableTpa       whether the TPA features are enabled
 */
public final record Config(
    String defaultLanguage,
    int maxHomes,
    int requestTimeout,
    boolean enableSpawn,
    boolean enableWarps,
    boolean enableHomes,
    boolean enableBack,
    boolean enableTpa) {
}

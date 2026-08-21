package timewaster.publicteleport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import timewaster.publicteleport.commands.Registrar;

// TODO: multi-modloader compatibility

/**
 * Entry point of the Public Teleport mod.
 */
public class PublicTeleport implements ModInitializer {
    public static final String MOD_ID = "public-teleport";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Storage storage = new Storage();

    @Override
    public void onInitialize() {
        Registrar.registerCommands();
        Teleports.registerDeathEvent();
        Requests.registerTickEvent();

        LOGGER.info(prefix("Initialized!"));
    }

    /**
     * Workaround to show the {@link MOD_ID} in log messages. According to
     * https://docs.fabricmc.net/develop/debugging getting the logger with
     * {@code .getLogger(MOD_ID)} should do that automatically, but it is broken.
     *
     * @param text the message to prefix
     * @return the prefixed {@code text}
     */
    public static String prefix(String text) {
        return "[" + MOD_ID + "]: " + text;
    }
}

package timewaster.publicteleport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.commands.Registrar;
import timewaster.publicteleport.records.Teleport;

public class PublicTeleport implements ModInitializer {

    private static final String MOD_ID = "public-teleport";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private final Storage storage = new Storage(MOD_ID, LOGGER);
    private final Teleports teleports = new Teleports(storage);
    private final Requests requests = new Requests(storage, teleports);
    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        new Registrar(storage, requests, teleports);

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, cause) -> {
            if (entity instanceof ServerPlayer player) {
                storage.setTeleport(player, Teleport.create(player, "back"), false);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            // 20 ticks = 1 second
            if (tickCounter >= 20) {
                tickCounter = 0;

                requests.cleanup(server);
            }
        });

        LOGGER.info("(" + MOD_ID + ") initialized!");
    }
}

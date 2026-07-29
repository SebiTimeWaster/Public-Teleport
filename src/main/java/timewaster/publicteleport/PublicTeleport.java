package timewaster.publicteleport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

public class PublicTeleport implements ModInitializer {

    private static final String MOD_ID = "public-teleport";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private FileHandler fileHandler = new FileHandler(MOD_ID, LOGGER);
    private TeleportHandler teleportHandler = new TeleportHandler(fileHandler);
    private RequestHandler requestHandler = new RequestHandler(teleportHandler);
    private CommandHandler commandHandler = new CommandHandler(fileHandler, requestHandler, teleportHandler);

    @Override
    public void onInitialize() {
        commandHandler.registerCommands();

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, cause) -> {
            if (entity instanceof ServerPlayer player) {
                fileHandler.setTeleport(player.getUUID(), Teleport.create(player, "back"));
            }
        });

        LOGGER.info(MessageHandler.getMessage("init"));
    }
}

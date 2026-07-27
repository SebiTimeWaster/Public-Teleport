package timewaster.publicteleport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

public class PublicTeleport implements ModInitializer {

    static final String MOD_ID = "public-teleport";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public FileHandler fileHandler = new FileHandler(MOD_ID, LOGGER);
    public RequestHandler requestHandler = new RequestHandler(fileHandler);
    public CommandHandler commandHandler = new CommandHandler(fileHandler, requestHandler);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> commandHandler.registerCommands(dispatcher));

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, cause) -> {
            if (entity instanceof ServerPlayer player) {
                fileHandler.setTeleport(player.getUUID(), Teleport.create(player, "back"));
            }
        });

        LOGGER.info("Initialized!");
    }
}

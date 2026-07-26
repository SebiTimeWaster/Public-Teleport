package timewaster.publicteleport;

import java.util.List;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

public class TeleportHandler {
    private static void doTeleportEffect(ServerLevel world, ServerPlayer player) {
        world.playSound(
                null,
                player.getBlockX() + 0.5,
                player.getBlockY() + 0.5,
                player.getBlockZ() + 0.5,
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS,
                1.0f,
                1.0f);

        world.sendParticles(
                ParticleTypes.PORTAL,
                player.getBlockX() + 0.5,
                player.getBlockY() + 0.5,
                player.getBlockZ() + 0.5,
                25,
                0.25,
                0.25,
                0.25,
                0.0);
    }

    @SuppressWarnings("null")
    public static boolean teleport(ServerPlayer player, Teleport teleport) {
        if (teleport == null) {
            MessageHandler.sendMessage(player, "no_teleport");
            return false;
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(teleport.dimension()));
        ServerLevel world = player.level().getServer().getLevel(dimension);
        boolean isHomeOrBack = List.of("home", "back").contains(teleport.name());

        if (world == null) {
            MessageHandler.sendMessage(player, "no_dimension");
            return false;
        }

        player.teleportTo(
                world,
                teleport.x() + 0.5,
                teleport.y() + 0.05,
                teleport.z() + 0.5,
                Set.of(),
                teleport.yaw() != null ? (float) teleport.yaw() : player.getYRot(),
                teleport.pitch() != null ? (float) teleport.pitch() : player.getXRot(),
                true);

        doTeleportEffect(world, player);

        MessageHandler.sendMessage(player, isHomeOrBack ? "teleported" : "teleported_to", teleport.name());

        return true;
    }
}

package timewaster.publicteleport;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import timewaster.publicteleport.records.Teleport;

public class Teleports {
    private final Storage storage;

    public Teleports(Storage storage) {
        this.storage = storage;
    }

    @SuppressWarnings("null")
    private static void doTeleportEffect(ServerLevel level, ServerPlayer player) {
        level.playSound(
            null,
            player.getBlockX() + 0.5,
            player.getBlockY() + 1.0,
            player.getBlockZ() + 0.5,
            SoundEvents.ENDERMAN_TELEPORT,
            SoundSource.PLAYERS,
            1.0f,
            1.0f);

        level.sendParticles(
            ParticleTypes.PORTAL,
            true,
            true,
            player.getX(),
            player.getY() + 0.75,
            player.getZ(),
            100,
            0.5,
            0.75,
            0.5,
            0.25);
    }

    private static boolean teleport(ServerPlayer player, Teleport target) {
        ServerLevel level = player.level();
        boolean isHomeOrBack = List.of("home", "back").contains(target.name());

        boolean result = player.teleportTo(
            level,
            target.x() + 0.5,
            target.y() + 0.05,
            target.z() + 0.5,
            Objects.requireNonNull(Set.of()),
            target.yaw() != null ? (float) target.yaw() : player.getYRot(),
            target.pitch() != null ? (float) target.pitch() : player.getXRot(),
            true);

        if (result) {
            doTeleportEffect(level, player);
            Messages.sendMessage(player, isHomeOrBack ? "teleported" : "teleported_to", target.name());
        } else {
            Messages.sendMessage(player, "unknown_error");
        }

        return result;
    }

    private boolean teleportPlayerImpl(ServerPlayer player, Teleport target, String targetName, boolean isWarp) {
        if (target == null) {
            Messages.sendMessage(player, isWarp ? "warp_no_exist" : "home_no_exist", targetName);
            return false;
        }

        Teleport back = Teleport.create(player, "back");
        boolean result = teleport(player, target);

        if (result && target.name() != "back") {
            storage.setTeleport(player.getUUID(), back);
        }

        return result;
    }

    public boolean teleportPlayer(ServerPlayer player, String target, boolean isWarp) {
        Teleport teleportTarget = storage.getTeleport(isWarp ? null : player.getUUID(), target);

        return teleportPlayerImpl(player, teleportTarget, target, isWarp);
    }

    public boolean teleportPlayer(ServerPlayer player, Teleport target) {
        return teleportPlayerImpl(player, target, target.name(), false);
    }

    public static boolean listTeleportNames(ServerPlayer player, List<String> teleportNames, boolean isWarps) {
        if (teleportNames == null) {
            Messages.sendMessage(player, "unknown_error");
            return false;
        }

        if (teleportNames.size() == 0) {
            Messages.sendMessage(player, isWarps ? "no_warps" : "no_homes");
        } else {
            Messages.Builder builder = new Messages.Builder().append(isWarps ? "headline_warps" : "headline_homes");

            for (String name : teleportNames) {
                builder.append("line");
                builder.button(Messages.getMessage("button_named", name), isWarps ? "/warp " + name : "/home " + name,
                    Messages.getMessage("teleport_to", name));
            }

            builder.send(player);
        }

        return true;
    }
}

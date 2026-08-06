package timewaster.publicteleport;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import timewaster.publicteleport.records.Teleport;

public class Teleports {
    private final Storage storage;

    public Teleports(Storage storage) {
        this.storage = storage;
    }

    @SuppressWarnings("null")
    private static void doTeleportEffect(ServerPlayer player) {
        player.level().playSound(
            null,
            player.getBlockX() + 0.5,
            player.getBlockY() + 1.0,
            player.getBlockZ() + 0.5,
            SoundEvents.ENDERMAN_TELEPORT,
            SoundSource.PLAYERS,
            1.0f,
            1.0f);

        player.level().sendParticles(
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

    @SuppressWarnings("unused")
    private static boolean teleport(ServerPlayer player, Teleport target) {
        Identifier dimId = Identifier.parse(Objects.requireNonNull(target.dimension()));
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        ServerLevel level = player.level().getServer().getLevel(dimKey);

        if (level == null) {
            Messages.sendMessage(player, "level_no_exist", Messages.Type.ERROR);
            return false;
        }

        doTeleportEffect(player);

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
            doTeleportEffect(player);
        } else {
            Messages.sendMessage(player, "unknown_error", Messages.Type.ERROR);
        }

        return result;
    }

    private boolean teleportPlayer(ServerPlayer player, Teleport target, String originalTargetName, boolean isWarp) {
        if (target == null) {
            return false;
        }

        if (target.name().equals("public_teleport_not_found")) {
            Messages.sendMessage(player, isWarp ? "warp_no_exist" : "home_no_exist", Messages.Type.ERROR,
                originalTargetName);
            return false;
        }

        if (Math.abs(target.x() - Math.floor(player.getX())) <= 1 &&
            Math.abs(target.y() - Math.ceil(player.getY())) <= 1 &&
            Math.abs(target.z() - Math.floor(player.getZ())) <= 1) {
            Messages.sendMessage(player, "teleport_unnecessary", Messages.Type.TEXT, originalTargetName);
            return false;
        }

        Teleport back = Teleport.create(player, "back");
        boolean result = teleport(player, target);

        if (result) {
            if (!target.name().equals("back")) {
                storage.setTeleport(player, back, false);
            }

            Messages.sendMessage(player, "teleported_to", Messages.Type.SUCCESS,
                target.name());
        }

        return result;
    }

    public boolean teleportPlayer(ServerPlayer player, String originalTargetName, boolean isWarp) {
        Teleport teleportTarget = storage.getTeleport(player, originalTargetName, isWarp);

        if (teleportTarget == null) {
            return false;
        }

        return teleportPlayer(player, teleportTarget, originalTargetName, isWarp);
    }

    public boolean teleportPlayer(ServerPlayer player, Teleport target) {
        return teleportPlayer(player, target, target.name(), false);
    }

    public static boolean listTeleportNames(ServerPlayer player, List<String> teleportNames, boolean isWarps) {
        if (teleportNames == null) {
            Messages.sendMessage(player, "unknown_error", Messages.Type.ERROR);
            return false;
        }

        if (teleportNames.size() == 0) {
            Messages.sendMessage(player, isWarps ? "warp_none" : "home_none", Messages.Type.TEXT);
        } else {
            Messages.Builder builder = new Messages.Builder().append(isWarps ? "headline_warps" : "headline_homes");

            for (String name : teleportNames) {
                MutableComponent buttonText = Messages.getMessage("button_named", Messages.Type.BUTTON, name);
                MutableComponent hoverText = Messages.getMessage("teleport_to", null, name);
                String command = isWarps ? "/warp " + name : "/home " + name;

                builder.append("\n  ").button(buttonText, hoverText, command);
            }

            builder.send(player);
        }

        return true;
    }
}

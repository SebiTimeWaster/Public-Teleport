package timewaster.publicteleport;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public class FileHandler {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private String modId;
    private Logger logger;

    public record Teleport(String name, int x, int y, int z, Float yaw, Float pitch, String dimension) {

    }

    public FileHandler(String modId, Logger logger) {
        this.modId = modId;
        this.logger = logger;
    }

    private Path getDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(modId);
    }

    public File getFile(MinecraftServer server, @Nullable UUID uuid) {
        Path worldDir = getDir(server);
        Path path = (uuid == null) ? worldDir.resolve("warps.json") : worldDir.resolve("homes/" + uuid + ".json");
        return path.toFile();
    }

    public void createDir(MinecraftServer server) {
        try {
            Files.createDirectories(getDir(server).resolve("homes"));
        } catch (IOException e) {
            logger.error("Failed to create data directory", e);
        }
    }

    public Teleport[] getWarps(File file) {
        if (!file.exists()) {
            return new Teleport[0];
        }

        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, Teleport[].class);
        } catch (IOException e) {
            logger.error("Failed to load warps from {}", file, e);
            return new Teleport[0];
        }
    }

    @Nullable
    public Teleport getWarp(MinecraftServer server, String name, @Nullable UUID uuid) {
        for (Teleport warp : getWarps(getFile(server, uuid))) {
            if (warp.name().equals(name)) {
                return warp;
            }
        }
        return null;
    }

    private void writeFile(File file, Object object) {
        try {
            Files.createDirectories(file.getParentFile().toPath());

            Path tempFile = Files.createTempFile(file.getParentFile().toPath(), "tmp-", ".json");
            try (FileWriter writer = new FileWriter(tempFile.toFile())) {
                GSON.toJson(object, writer);
            }

            Files.move(
                    tempFile,
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("Failed to save warps to {}", file, e);
        }
    }

    public void setWarp(String name, ServerPlayer player, @Nullable UUID uuid) {
        MinecraftServer server = player.level().getServer();
        ArrayList<Teleport> warps = new ArrayList<>(List.of(getWarps(getFile(server, uuid))));
        String dimension = player.level().dimension().identifier().toString();
        Teleport warp = new Teleport(
                name,
                (int) Math.floor(player.getX()),
                (int) Math.ceil(player.getY()),
                (int) Math.floor(player.getZ()),
                (Float) player.getYRot(),
                (Float) player.getXRot(),
                dimension);

        boolean warpExists = false;
        for (int i = 0; i < warps.size(); i++) {
            if (warps.get(i).name().equals(name)) {
                warps.set(i, warp);
                warpExists = true;
            }
        }

        if (!warpExists) {
            warps.add(warp);
        }

        CompletableFuture.runAsync(() -> writeFile(getFile(server, uuid), warps));
    }

    public int delWarp(String name, ServerPlayer player, @Nullable UUID uuid) {
        MinecraftServer server = player.level().getServer();
        ArrayList<Teleport> warps = new ArrayList<>(List.of(getWarps(getFile(server, uuid))));

        int delIndex = -1;
        for (int i = 0; i < warps.size(); i++) {
            if (warps.get(i).name().equals(name)) {
                delIndex = i;
                break;
            }
        }

        String start = uuid == null ? "Warp '" : "Home '";

        if (delIndex == -1) {
            player.sendSystemMessage(
                    Component.literal(start + name + "' does not exist!").withStyle(ChatFormatting.RED));
            return 0;
        } else {
            warps.remove(delIndex);
            CompletableFuture.runAsync(() -> writeFile(getFile(server, uuid), warps));

            player.sendSystemMessage(Component.literal(start + name + "' deleted!").withStyle(ChatFormatting.AQUA));
            return 1;
        }
    }
}

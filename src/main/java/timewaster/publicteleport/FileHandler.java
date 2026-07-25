package timewaster.publicteleport;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

public class FileHandler {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private Path pathConfig;
    private Path pathConfigHomes;
    private Logger logger;
    private File fileConfig;
    private File fileWarps;
    private List<Teleport> warps;
    private Config config;
    private Config configDefault = new Config(10, true, true, true, true, true);

    public record Teleport(String name, int x, int y, int z, Float yaw, Float pitch, String dimension) {

    }

    public record Config(int maxHomes, boolean enableSpawn, boolean enableWarps, boolean enableHomes,
            boolean enableBack, boolean enableTpa) {

    }

    public FileHandler(String modId, Logger logger) {
        this.pathConfig = FabricLoader.getInstance().getConfigDir().resolve(modId);
        this.pathConfigHomes = pathConfig.resolve("homes");
        this.logger = logger;
        this.fileConfig = pathConfig.resolve("config.json").toFile();
        this.fileWarps = pathConfig.resolve("warps.json").toFile();
        createDirectories();
        this.config = loadFile(fileConfig, configDefault, Config.class);
        this.warps = loadFile(fileWarps);
    }

    private void createDirectories() {
        try {
            Files.createDirectories(pathConfigHomes);
        } catch (IOException e) {
            logger.error("Failed to create config directories!");
            throw new UncheckedIOException(e);
        }
    }

    private <T> T loadFile(File file, T defaultValue, Type type) {
        if (!file.exists()) {
            saveFile(file, defaultValue);
            return defaultValue;
        }

        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            logger.error("Failed to load from {}:", file, e);
            return defaultValue;
        }
    }

    private List<Teleport> loadFile(File file) {
        Type type = new TypeToken<List<Teleport>>() {
        }.getType();

        return loadFile(file, new ArrayList<>(), type);
    }

    private void saveFile(File file, Object data) {
        try {
            Path tempPath = Files.createTempFile(file.getParentFile().toPath(), "tmp-", ".json");

            try (FileWriter writer = new FileWriter(tempPath.toFile())) {
                GSON.toJson(data, writer);
            }

            Files.move(tempPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("Failed to save to {}:", file, e);
        }
    }

    private File getHomeFileByUuid(UUID uuid) {
        return pathConfigHomes.resolve(uuid + ".json").toFile();
    }

    private List<Teleport> getTeleports(@Nullable UUID uuid) {
        List<Teleport> teleports;

        if (uuid != null) {
            teleports = loadFile(getHomeFileByUuid(uuid));
        } else {
            teleports = warps;
        }

        return teleports;
    }

    private void setTeleports(@Nullable UUID uuid, List<Teleport> teleports) {
        if (uuid != null) {
            saveFile(getHomeFileByUuid(uuid), teleports);
        } else {
            warps = teleports;
            saveFile(fileWarps, teleports);
        }
    }

    @Nullable
    public Teleport getTeleport(@Nullable UUID uuid, String name) {
        for (Teleport teleport : getTeleports(uuid)) {
            if (teleport.name().equals(name)) {
                return teleport;
            }
        }

        return null;
    }

    public List<String> getTeleportNames(@Nullable UUID uuid) {
        List<String> names = new ArrayList<String>();

        for (Teleport teleport : getTeleports(uuid)) {
            names.add(teleport.name);
        }

        Collections.sort(names);

        return names;
    }

    public void setTeleport(@Nullable UUID uuid, Teleport newTeleport) {
        List<Teleport> teleports = getTeleports(uuid);
        int index = -1;

        for (int i = 0; i < teleports.size(); i++) {
            if (teleports.get(i).name().equals(newTeleport.name)) {
                teleports.set(i, newTeleport);
                index = i;
            }
        }

        if (index == -1) {
            teleports.add(newTeleport);
        }

        setTeleports(uuid, teleports);
    }

    public boolean deleteTeleport(@Nullable UUID uuid, String name) {
        List<Teleport> teleports = getTeleports(uuid);
        int index = -1;

        for (int i = 0; i < teleports.size(); i++) {
            if (teleports.get(i).name().equals(name)) {
                teleports.remove(i);
                index = i;
            }
        }

        if (index == -1) {
            return false;
        } else {
            setTeleports(uuid, teleports);
            return true;
        }
    }

    public Config getConfig() {
        return config;
    }
}

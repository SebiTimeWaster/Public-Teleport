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

/**
 * Handles all persistence for the mod: reading and writing the mod's
 * configuration file, the shared "warps" list, and per-player "homes" lists.
 * <p>
 * Data is stored as JSON on disk under the Fabric config directory
 * (e.g. {@code config/<modId>/}), with one file for the global config,
 * one file for warps, and one file per player (named by UUID) under the
 * {@code homes} subdirectory.
 * <p>
 * This class is not thread-safe; callers are expected to only access it
 * from a single thread (e.g. the server thread) at a time.
 */
public class FileHandler {

    /** Fallback configuration used when no config file exists yet. */
    private static final Config configDefault = new Config(10, true, true, true, true, true);
    /** Shared Gson instance used for all JSON (de)serialization. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Directory containing this mod's config, e.g. {@code config/<modId>/}. */
    private Path pathConfig;
    /** Path for per-player home files, e.g. {@code config/<modId>/homes/}. */
    private Path pathConfigHomes;
    /** Logger used to report I/O failures. */
    private Logger logger;
    /** The {@code config.json} file on disk. */
    private File fileConfig;
    /** The {@code warps.json} file on disk. */
    private File fileWarps;
    /** Cache of the shared warp list, kept in sync with {@link #fileWarps}. */
    private List<Teleport> warps;
    /** The currently loaded mod configuration. */
    private Config config;

    /**
     * A single named teleport destination (used for both warps and homes).
     *
     * @param name      the unique (per-list) name identifying this teleport
     * @param x         block x-coordinate
     * @param y         block y-coordinate
     * @param z         block z-coordinate
     * @param yaw       facing yaw in degrees, or {@code null} if not set
     * @param pitch     facing pitch in degrees, or {@code null} if not set
     * @param dimension identifier of the dimension/world this teleport belongs to
     */
    public record Teleport(String name, int x, int y, int z, Float yaw, Float pitch, String dimension) {

    }

    /**
     * Mod-wide configuration options.
     *
     * @param maxHomes    maximum number of homes a single player may set
     * @param enableSpawn whether the spawn features are enabled
     * @param enableWarps whether warps are enabled
     * @param enableHomes whether homes are enabled
     * @param enableBack  whether the "back" (return to previous location) feature
     *                    is enabled
     * @param enableTpa   whether player-to-player teleport requests (tpa) are
     *                    enabled
     */
    public record Config(int maxHomes, boolean enableSpawn, boolean enableWarps, boolean enableHomes,
            boolean enableBack, boolean enableTpa) {

    }

    /**
     * Creates a new file handler, ensuring the config directories exist and
     * loading the config and warps from disk (creating them with default
     * values if they don't already exist).
     *
     * @param modId  the mod's identifier, used to resolve the config directory
     * @param logger logger used to report I/O errors during load/save operations
     * @throws UncheckedIOException if the config directories and config/data files
     *                              cannot be created
     */
    public FileHandler(String modId, Logger logger) {
        this.pathConfig = FabricLoader.getInstance().getConfigDir().resolve(modId);
        this.pathConfigHomes = pathConfig.resolve("homes");
        this.logger = logger;
        this.fileConfig = pathConfig.resolve("config.json").toFile();
        this.fileWarps = pathConfig.resolve("warps.json").toFile();
        createDirectories();
        this.config = loadFile(fileConfig, configDefault, Config.class, true);
        this.warps = loadFile(fileWarps, true);
    }

    /**
     * Ensures the mod's config directory and the homes subdirectory exist,
     * creating any missing parent directories as needed.
     *
     * @throws UncheckedIOException if directory creation fails
     */
    private void createDirectories() {
        try {
            Files.createDirectories(pathConfigHomes);
        } catch (IOException e) {
            logger.error("Failed to create config directories!");
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Loads and deserializes JSON data from the given file.
     * <p>
     * If the file does not exist yet it is created, {@code defaultValue} is written
     * to it and then returned. If reading or parsing fails, the error is logged and
     * {@code defaultValue} is returned.
     *
     * @param file         the file to read from
     * @param defaultValue the value to use (and persist) if the file is missing,
     *                     and to fall back to if loading fails
     * @param type         the Gson type to deserialize into
     * @return the deserialized value, or {@code defaultValue} if the file was
     *         missing or could not be read
     * @throws UncheckedIOException if the file could not be loaded and
     *                              {@code failOnError} is true
     */
    private <T> T loadFile(File file, T defaultValue, Type type, boolean failOnError) {
        if (!file.exists()) {
            saveFile(file, defaultValue, failOnError);
            return defaultValue;
        }

        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            logger.error("Failed to load from {}:", file, e);
            if (failOnError) {
                throw new UncheckedIOException(e);
            } else {
                return defaultValue;
            }
        }
    }

    /**
     * Overload to specifically load an arraylist of {@link Teleport} entries from
     * the given file.
     *
     * @param file the file to read the teleport list from
     * @return the list of teleports stored in the file, or an empty list if
     *         the file was missing or could not be read
     */
    private List<Teleport> loadFile(File file, boolean failOnError) {
        Type type = new TypeToken<List<Teleport>>() {
        }.getType();

        return loadFile(file, new ArrayList<>(), type, failOnError);
    }

    /**
     * Serializes {@code data} to JSON and writes it to {@code file} atomically.
     * <p>
     * Writes are performed atomically: data is first written to a temporary
     * file in the same directory, then moved into place with
     * {@link StandardCopyOption#ATOMIC_MOVE}, so a crash or power loss during
     * a save cannot leave a half-written or corrupted file behind.
     * <p>
     * Failures are logged rather than thrown, so callers should not assume
     * a save always succeeds.
     *
     * @param file the destination file to write to
     * @param data the object to serialize as JSON
     * @throws UncheckedIOException if the file could not be saved and
     *                              {@code failOnError} is true
     */
    private void saveFile(File file, Object data, boolean failOnError) {
        try {
            Path tempPath = Files.createTempFile(file.getParentFile().toPath(), "tmp-", ".json");

            try (FileWriter writer = new FileWriter(tempPath.toFile())) {
                GSON.toJson(data, writer);
            }

            Files.move(tempPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("Failed to save to {}:", file, e);
            if (failOnError) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Resolves the on-disk JSON file used to store a given player's homes.
     *
     * @param uuid the player's unique id
     * @return the {@code <uuid>.json} file under the homes directory
     */
    private File getHomeFileByUuid(UUID uuid) {
        return pathConfigHomes.resolve(uuid + ".json").toFile();
    }

    /**
     * Returns the current list of teleports for the given player, or the
     * shared warps list if {@code uuid} is {@code null}.
     * <p>
     * When {@code uuid} is non-null, the player's homes are loaded fresh
     * from disk on every call. When {@code uuid} is {@code null}, the
     * in-memory {@link #warps} cache is returned directly.
     *
     * @param uuid the player's unique id, or {@code null} to access warps
     * @return the mutable list of teleports for the requested scope
     */
    private List<Teleport> loadTeleports(@Nullable UUID uuid) {
        List<Teleport> teleports;

        if (uuid != null) {
            teleports = loadFile(getHomeFileByUuid(uuid), false);
        } else {
            teleports = warps;
        }

        return teleports;
    }

    /**
     * Persists the given list of teleports for the given player or shared warps
     * list if {@code uuid} is {@code null}.
     *
     * @param uuid      the player's unique id, or {@code null} to update warps
     * @param teleports the full list of teleports to save
     */
    private void saveTeleports(@Nullable UUID uuid, List<Teleport> teleports) {
        if (uuid != null) {
            saveFile(getHomeFileByUuid(uuid), teleports, false);
        } else {
            warps = teleports;
            saveFile(fileWarps, teleports, false);
        }
    }

    /**
     * Returns a single teleport by name.
     *
     * @param uuid the player's unique id, or {@code null} to search warps
     * @param name the exact name of the teleport to find
     * @return the matching {@link Teleport}, or {@code null} if none exists
     *         with that name in the requested scope
     */
    @Nullable
    public Teleport getTeleport(@Nullable UUID uuid, String name) {
        for (Teleport teleport : loadTeleports(uuid)) {
            if (teleport.name().equals(name)) {
                return teleport;
            }
        }

        return null;
    }

    /**
     * Returns the names of all teleports in the requested scope, sorted
     * alphabetically.
     *
     * @param uuid the player's unique id, or {@code null} to list warp names
     * @return an alphabetically sorted list of teleport names
     */
    public List<String> getTeleportNames(@Nullable UUID uuid) {
        List<String> names = new ArrayList<String>();

        for (Teleport teleport : loadTeleports(uuid)) {
            names.add(teleport.name);
        }

        Collections.sort(names);

        return names;
    }

    /**
     * Creates or updates a teleport with the given name and persists the
     * change to disk.
     * <p>
     * If a teleport with the same name already exists, it is replaced
     * in-place; otherwise the new teleport is added.
     *
     * @param uuid        the player's unique id, or {@code null} to modify warps
     * @param newTeleport the teleport to add or update, identified by its name
     */
    public void setTeleport(@Nullable UUID uuid, Teleport newTeleport) {
        List<Teleport> teleports = loadTeleports(uuid);
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

        saveTeleports(uuid, teleports);
    }

    /**
     * Deletes the teleport with the given name (If it exists) and persists the
     * change to disk.
     *
     * @param uuid the player's unique id, or {@code null} to modify warps
     * @param name the teleport to delete, identified by its name
     * @return {@code true} if a teleport was found and removed, {@code false}
     *         if no teleport with that name existed
     */
    public boolean deleteTeleport(@Nullable UUID uuid, String name) {
        List<Teleport> teleports = loadTeleports(uuid);
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
            saveTeleports(uuid, teleports);
            return true;
        }
    }

    /**
     * Returns the mod configuration.
     *
     * @return the active {@link Config}
     */
    public Config getConfig() {
        return config;
    }
}

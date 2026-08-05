package timewaster.publicteleport;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;
import timewaster.publicteleport.records.Config;
import timewaster.publicteleport.records.Teleport;

/**
 * Handles all persistence for the mod: reading and writing the mod's
 * configuration file, the shared "warps" list, and per-player "homes"
 * lists.
 * <p>
 * Data is stored as JSON on disk under the Fabric config directory (e.g.
 * {@code config/<modId>/}), with one file for the global config, one
 * file for warps, and one file per player (named by UUID) under the
 * {@code homes} subdirectory.
 * <p>
 * This class is not thread-safe; callers are expected to only access it from a
 * single thread (e.g. the server thread) at a time.
 */
public class Storage {

    /** Fallback configuration used when no config file exists yet. */
    private static final Config configDefault = new Config(10, 60, true, true, true, true, true);
    /** Shared Gson instance used for all JSON (de)serialization. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Directory containing this mod's config, e.g. {@code config/<modId>/}. */
    private final Path pathConfig;
    /** Path for per-player home files, e.g. {@code config/<modId>/homes/}. */
    private final Path pathConfigHomes;
    /** Logger used to report I/O failures. */
    private final Logger logger;
    /** The {@code config.json} file on disk. */
    private final File fileConfig;
    /** The {@code warps.json} file on disk. */
    private final File fileWarps;
    /** The currently loaded mod configuration. */
    private final Config config;
    /** Cache of the shared warp list, kept in sync with {@link #fileWarps}. */
    private List<Teleport> warps;

    /**
     * Creates a new file handler, ensuring the config directories exist and loading
     * the config and warps from disk (creating them with
     * default values if they don't already exist).
     *
     * @param modId  the mod's identifier, used to resolve the config directory
     * @param logger logger used to report I/O errors during load/save operations
     * @throws UncheckedIOException if the config directories and config/data files
     *                                  cannot be created
     */
    public Storage(String modId, Logger logger) {
        this.pathConfig = FabricLoader.getInstance().getConfigDir().resolve(modId);
        this.pathConfigHomes = pathConfig.resolve("homes");
        this.logger = logger;
        this.fileConfig = pathConfig.resolve("config.json").toFile();
        this.fileWarps = pathConfig.resolve("warps.json").toFile();
        createDirectories();
        this.config = loadConfig();
        this.warps = loadFile(fileWarps, true);
    }

    /**
     * Ensures the mod's config directory and the homes subdirectory exist, creating
     * any missing parent directories as needed.
     *
     * @throws UncheckedIOException if directory creation fails
     */
    private void createDirectories() {
        try {
            Files.createDirectories(pathConfigHomes);
        } catch (IOException e) {
            logger.error(Messages.getMessage("err_create_dir"));
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Loads and deserializes JSON data from the config file.
     * <p>
     * If the file {@code fileConfig} does not exist yet it is created,
     * {@code configDefault} is written to it and then used.
     *
     * @throws UncheckedIOException if the config could not be loaded
     */
    private Config loadConfig() {
        if (!fileConfig.exists()) {
            saveFile(fileConfig, configDefault, true);
            return configDefault;
        }

        try (BufferedReader reader = Files.newBufferedReader(fileConfig.toPath(), StandardCharsets.UTF_8)) {
            JsonObject defaultObj = GSON.toJsonTree(configDefault).getAsJsonObject();
            JsonObject fileObj = JsonParser.parseReader(reader).getAsJsonObject();
            boolean changed = false;

            for (String key : defaultObj.keySet()) {
                if (!fileObj.has(key)) {
                    fileObj.add(key, defaultObj.get(key));
                    changed = true;
                }
            }

            @SuppressWarnings("null")
            Config mergedConfig = GSON.fromJson(fileObj, Config.class);

            if (changed) {
                saveFile(fileConfig, mergedConfig, true);
            }

            return mergedConfig;
        } catch (IOException e) {
            logger.error(Messages.getMessage("err_load_config"), fileConfig, e);
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Loads and deserializes JSON data from the given file as a {@code List} of
     * {@code Teleport}.
     * <p>
     * If the file does not exist yet it is created, {@code defaultValue} is written
     * to it and then returned.
     *
     * @param file        the file to read from
     * @param failOnError if an UncheckedIOException should be thrown on error
     * @return the deserialized value, or {@code defaultValue} if the file does not
     *         exist, or null if the file could not be read and
     *         {@code failOnError} is false
     * @throws UncheckedIOException if the file could not be loaded and
     *                                  {@code failOnError} is true
     */
    private @Nullable List<Teleport> loadFile(File file, boolean failOnError) {
        Type listType = new TypeToken<List<Teleport>>() {
        }.getType();
        List<Teleport> defaultValue = new ArrayList<Teleport>();

        if (!file.exists()) {
            saveFile(file, defaultValue, failOnError);
            return defaultValue;
        }

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, listType);
        } catch (IOException e) {
            logger.error(Messages.getMessage("err_load_file"), file, e);
            if (failOnError) {
                throw new UncheckedIOException(e);
            } else {
                return null;
            }
        }
    }

    /**
     * Serializes {@code data} to JSON and writes it to {@code file} atomically.
     * <p>
     * Writes are performed atomically: data is first written to a temporary file in
     * the same directory, then moved into place with
     * {@link StandardCopyOption#ATOMIC_MOVE}, so a crash or power loss during a
     * save cannot leave a half-written or corrupted file behind.
     *
     * @param file the destination file to write to
     * @param data the object to serialize as JSON
     * @return true if save was successful, false if file could not be saved
     * @throws UncheckedIOException if the file could not be saved and
     *                                  {@code failOnError} is true
     */
    private boolean saveFile(File file, Object data, boolean failOnError) {
        try {
            Path tempPath = Files.createTempFile(file.getParentFile().toPath(), "tmp-", ".json");

            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE)) {
                GSON.toJson(data, writer);
                writer.flush();
            }

            Files.move(tempPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error(Messages.getMessage("err_save_file"), file, e);
            if (failOnError) {
                throw new UncheckedIOException(e);
            } else {
                return false;
            }
        }

        return true;
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
     * Returns the current list of teleports for the given player, or the shared
     * warps list if {@code uuid} is {@code null}.
     *
     * @param uuid the player's unique id, or {@code null} to access warps
     * @return the mutable list of teleports for the requested scope
     */
    @Nullable
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
     * @return true if save was successful
     */
    private boolean saveTeleports(@Nullable UUID uuid, List<Teleport> teleports) {
        if (uuid != null) {
            return saveFile(getHomeFileByUuid(uuid), teleports, false);
        } else {
            warps = teleports;
            return saveFile(fileWarps, teleports, false);
        }
    }

    /**
     * Returns a single teleport by name.
     *
     * @param uuid the player's unique id, or {@code null} to search warps
     * @param name the exact name of the teleport to find
     * @return the matching {@link Teleport}, or {@code null} if none exists with
     *         that name in the requested scope
     */
    @Nullable
    public Teleport getTeleport(@Nullable UUID uuid, String name) {
        List<Teleport> teleports = loadTeleports(uuid);

        if (teleports != null) {
            for (Teleport teleport : teleports) {
                if (teleport.name().equals(name)) {
                    return teleport;
                }
            }
        }

        return null;
    }

    /**
     * Returns the names of all teleports in the requested scope, sorted
     * alphabetically, special keywords "back" and "spawn" are filtered out of homes
     * and warps respectively.
     *
     * @param uuid the player's unique id, or {@code null} to list warp names
     * @return an alphabetically sorted list of teleport names
     */
    public List<String> getTeleportNames(@Nullable UUID uuid) {
        List<String> names = new ArrayList<String>();
        List<Teleport> teleports = loadTeleports(uuid);

        if (teleports != null) {
            for (Teleport teleport : teleports) {
                if ((uuid != null && !teleport.name().equals("back")) ||
                    (uuid == null && !teleport.name().equals("spawn"))) {
                    names.add(teleport.name());
                }
            }

            Collections.sort(names);
        }

        return names;
    }

    /**
     * Creates or updates a teleport with the given name and persists the change to
     * disk.
     * <p>
     * If a teleport with the same name already exists, it is replaced in-place;
     * otherwise the new teleport is added.
     * <p>
     * Checks if the Homes limit is reached, but the special Home "back" is exempt
     * from the limit.
     *
     * @param uuid        the player's unique id, or {@code null} to modify warps
     * @param newTeleport the teleport to add or update, identified by its name
     * @return {@code false} if a new teleport would go over the {@code maxHomes}
     *         limit and is not saved
     */
    public boolean setTeleport(@Nullable UUID uuid, Teleport newTeleport) {
        List<Teleport> teleports = loadTeleports(uuid);

        if (teleports != null) {
            boolean exists = false;
            int numTeleports = 0;

            for (int i = 0; i < teleports.size(); i++) {
                if (teleports.get(i).name().equals(newTeleport.name())) {
                    teleports.set(i, newTeleport);
                    exists = true;
                }

                if (!teleports.get(i).name().equals("back")) {
                    numTeleports++;
                }
            }

            if (!exists) {
                if (uuid != null && config.maxHomes() > 0 && config.maxHomes() <= numTeleports
                    && !newTeleport.name().equals("back")) {
                    return false;
                }

                teleports.add(newTeleport);
            }

            saveTeleports(uuid, teleports);
        }

        return true;
    }

    /**
     * Deletes the teleport with the given name if it exists and persists the change
     * to disk.
     *
     * @param uuid the player's unique id, or {@code null} to modify warps
     * @param name the teleport to delete, identified by its name
     * @return {@code true} if a teleport was found and removed
     */
    public boolean deleteTeleport(@Nullable UUID uuid, String name) {
        List<Teleport> teleports = loadTeleports(uuid);

        if (teleports != null) {
            boolean exists = false;

            for (int i = 0; i < teleports.size(); i++) {
                if (teleports.get(i).name().equals(name)) {
                    teleports.remove(i);
                    exists = true;
                }
            }

            if (exists) {
                saveTeleports(uuid, teleports);

                return true;
            }
        }

        return false;
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

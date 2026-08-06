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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.level.ServerPlayer;
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
    private static final Config configDefault = new Config("en_us", 10, 60, true, true, true, true, true);
    /** Shared Gson instance used for all JSON (de)serialization. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** The ID of this mod */
    private final String modId;
    /** Logger used to report I/O failures. */
    private final Logger logger;
    /** Directory containing this mod's config, e.g. {@code config/<modId>/}. */
    private final Path pathConfig;
    /** Path for per-player home files, e.g. {@code config/<modId>/homes/}. */
    private final Path pathConfigHomes;
    /** The {@code config.json} file on disk. */
    private final File fileConfig;
    /** The {@code warps.json} file on disk. */
    private final File fileWarps;
    /** The currently loaded mod configuration. */
    private final Config config;
    /** Cache of the shared warp list, kept in sync with {@link #fileWarps}. */
    private List<Teleport> warps;
    /** Cache of loaded user homes, kept in sync with the corresponding files. */
    private Map<UUID, List<Teleport>> homes = new HashMap<UUID, List<Teleport>>();

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
        this.modId = modId;
        this.logger = logger;
        this.pathConfig = FabricLoader.getInstance().getConfigDir().resolve(modId);
        this.pathConfigHomes = pathConfig.resolve("homes");
        this.fileConfig = pathConfig.resolve("config.json").toFile();
        this.fileWarps = pathConfig.resolve("warps.json").toFile();
        createDirectories();
        this.config = loadConfig();
        Messages.setModId(modId);
        Messages.setTranslations(loadTranslations());
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
            logger.error("Failed to create config directories!");
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
            logger.error("Failed to load config from: ", fileConfig.toPath().toString());
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, String> loadTranslations() {
        String languageFile = "assets/" + modId + "/lang/" + config.defaultLanguage() + ".json";
        FabricLoader fabricLoader = FabricLoader.getInstance();
        ModContainer modContainer;
        Path languagePath;

        try {
            modContainer = fabricLoader.getModContainer(modId).get();
        } catch (NoSuchElementException e) {
            logger.error("Could not find mod container!");
            throw new NoSuchElementException(e);
        }

        try {
            languagePath = modContainer.findPath(languageFile).get();
        } catch (NoSuchElementException e) {
            logger.error("Could not find language file!");
            throw new NoSuchElementException(e);
        }

        try (BufferedReader reader = Files.newBufferedReader(languagePath, StandardCharsets.UTF_8)) {
            TypeToken<Map<String, String>> mapType = new TypeToken<Map<String, String>>() {
            };

            return GSON.fromJson(reader, mapType);
        } catch (IOException e) {
            logger.error("Failed to load language file from: ", languagePath.toString());
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
        List<Teleport> defaultValue = new ArrayList<Teleport>();

        if (!file.exists()) {
            saveFile(file, defaultValue, failOnError);
            return defaultValue;
        }

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<Teleport>>() {
            }.getType();

            return GSON.fromJson(reader, listType);
        } catch (IOException e) {
            logger.error("Failed to load data from: ", file.toPath().toString());

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
     * @return true if save was successful
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
            logger.error("Failed to save data to: ", file.toPath().toString());

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
     * Returns the current list of teleports for the given player from cache or
     * file, or the shared warps list if {@code uuid} is {@code null}.
     *
     * @param uuid the player's unique id, or {@code null} to access warps
     * @return the mutable list of teleports for the requested scope or {@code null}
     *         if an error occured
     */
    @Nullable
    private List<Teleport> loadTeleports(@Nullable UUID uuid) {
        @Nullable
        List<Teleport> teleports;

        if (uuid != null) {
            teleports = homes.get(uuid);

            if (teleports == null) {
                teleports = loadFile(getHomeFileByUuid(uuid), false);

                if (teleports != null) {
                    homes.put(uuid, teleports);
                }
            }
        } else {
            teleports = warps;
        }

        return teleports;
    }

    /**
     * Caches and persists the given list of teleports for the given player or
     * shared warps list if {@code uuid} is {@code null}.
     *
     * @param uuid      the player's unique id, or {@code null} to update warps
     * @param teleports the full list of teleports to save
     * @return true if save was successful
     */
    private boolean saveTeleports(@Nullable UUID uuid, List<Teleport> teleports) {
        if (uuid != null) {
            homes.put(uuid, teleports);

            return saveFile(getHomeFileByUuid(uuid), teleports, false);
        } else {
            warps = teleports;

            return saveFile(fileWarps, teleports, false);
        }
    }

    /**
     * Returns a single teleport by name.
     *
     * @param player the player trying to load the data
     * @param name   the exact name of the teleport to find
     * @param isWarp if it is a warp, not a home
     * @return the matching {@link Teleport}, a new {@code Teleport} with name
     *         "public_teleport_not_found" if not found or {@code null} if a file
     *         error occured
     */
    @Nullable
    public Teleport getTeleport(ServerPlayer player, String name, boolean isWarp) {
        List<Teleport> teleports = loadTeleports(isWarp ? null : player.getUUID());

        if (teleports == null) {
            Messages.sendMessage(player, "data_not_loaded", Messages.Type.ERROR);
            return null;
        }

        for (Teleport teleport : teleports) {
            if (teleport.name().equals(name)) {
                return teleport;
            }
        }

        return Teleport.create(player, "public_teleport_not_found");
    }

    /**
     * Returns the names of all teleports in the requested scope, sorted
     * alphabetically, special keywords "back" and "spawn" are filtered out of homes
     * and warps respectively.
     *
     * @param player the player trying to load the data
     * @param isWarp if it is a warp, not a home
     * @return an alphabetically sorted list of teleport names or {@code null} if a
     *         file error occured
     */
    @Nullable
    public List<String> getTeleportNames(ServerPlayer player, boolean isWarp) {
        List<String> names = new ArrayList<String>();
        UUID uuid = player.getUUID();
        List<Teleport> teleports = loadTeleports(isWarp ? null : uuid);

        if (teleports == null) {
            Messages.sendMessage(player, "data_not_loaded", Messages.Type.ERROR);
            return null;
        }

        for (Teleport teleport : teleports) {
            if ((!isWarp && !teleport.name().equals("back")) ||
                (isWarp && !teleport.name().equals("spawn"))) {
                names.add(teleport.name());
            }
        }

        Collections.sort(names);

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
     * @param player      the player trying to load the data
     * @param newTeleport the teleport to add or update, identified by its name
     * @param isWarp      if it is a warp, not a home
     * @return {@code false} if a new teleport would go over the {@code maxHomes}
     *         limit and is not saved or {@code null} if a file error occured
     */
    @Nullable
    public Boolean setTeleport(ServerPlayer player, Teleport newTeleport, boolean isWarp) {
        UUID uuid = player.getUUID();
        List<Teleport> teleports = loadTeleports(isWarp ? null : uuid);
        boolean exists = false;
        int numTeleports = 0;

        if (teleports == null) {
            Messages.sendMessage(player, "data_not_loaded", Messages.Type.ERROR);
            return null;
        }

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
            if (!isWarp && config.maxHomes() > 0 && config.maxHomes() <= numTeleports
                && !newTeleport.name().equals("back")) {
                return false;
            }

            teleports.add(newTeleport);
        }

        if (!saveTeleports(isWarp ? null : uuid, teleports)) {
            Messages.sendMessage(player, "data_not_saved", Messages.Type.ERROR);
            return null;
        }

        return true;
    }

    /**
     * Deletes the teleport with the given name if it exists and persists the change
     * to disk.
     *
     * @param player the player trying to load the data
     * @param name   the teleport to delete, identified by its name
     * @param isWarp if it is a warp, not a home
     * @return {@code true} if a teleport was found and removed or {@code null} if a
     *         file error occured
     */
    public Boolean deleteTeleport(ServerPlayer player, String name, boolean isWarp) {
        UUID uuid = player.getUUID();
        List<Teleport> teleports = loadTeleports(isWarp ? null : uuid);
        boolean exists = false;

        if (teleports == null) {
            Messages.sendMessage(player, "data_not_loaded", Messages.Type.ERROR);
            return null;
        }

        for (int i = 0; i < teleports.size(); i++) {
            if (teleports.get(i).name().equals(name)) {
                teleports.remove(i);
                exists = true;
            }
        }

        if (exists) {
            if (!saveTeleports(isWarp ? null : uuid, teleports)) {
                Messages.sendMessage(player, "data_not_saved", Messages.Type.ERROR);
                return null;
            }

            return true;
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

package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.gametest.TestUtils.assertEqual;
import static timewaster.publicteleport.gametest.TestUtils.configWith;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.Storage;
import timewaster.publicteleport.records.Config;

/**
 * Loading of config.json, following the code of {@code Storage}. The file is
 * only read when the server starts, so these tests load it again with a
 * {@link Storage} of their own.
 */
@SuppressWarnings("null")
public class ConfigTest {
    private static final Gson GSON = new Gson();
    /** The options with a minimum value, see the README. */
    private static final Map<String, Integer> MINIMUMS = Map.of("maxHomes", 0, "requestTimeout", 10, "rtpRadius", 200);

    private static int value(Config config, String option) {
        return GSON.toJsonTree(config).getAsJsonObject().get(option).getAsInt();
    }

    private static int valueInFile(Path file, String option) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject().get(option).getAsInt();
    }

    /**
     * A value below the minimum is raised to the minimum, in the loaded config and
     * in the file. A value at the minimum is left alone.
     */
    @GameTest
    public void valuesBelowMinimumAreCorrected(GameTestHelper helper) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(PublicTeleport.MOD_ID).resolve("config.json");

        try {
            String original = Files.readString(file);

            try {
                for (Map.Entry<String, Integer> entry : MINIMUMS.entrySet()) {
                    String option = entry.getKey();
                    int minimum = entry.getValue();

                    Files.writeString(file, GSON.toJson(configWith(option, minimum - 1)));
                    assertEqual(helper, value(new Storage().getConfig(), option), minimum, "corrected \"" + option + "\"");
                    assertEqual(helper, valueInFile(file, option), minimum, "corrected \"" + option + "\" in config.json");

                    String atMinimum = GSON.toJson(configWith(option, minimum));
                    Files.writeString(file, atMinimum);
                    assertEqual(helper, value(new Storage().getConfig(), option), minimum, "\"" + option + "\" at its minimum");
                    assertEqual(helper, Files.readString(file), atMinimum, "config.json with \"" + option + "\" at its minimum");
                }
            } finally {
                Files.writeString(file, original);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        helper.succeed();
    }
}

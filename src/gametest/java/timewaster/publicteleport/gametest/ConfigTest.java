package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.gametest.TestUtils.assertEqual;
import static timewaster.publicteleport.gametest.TestUtils.configWith;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
    /** The default values, see the README. */
    private static final Config DEFAULTS = new Config("en_us", true, true, true, true, true, true, true, 10, 60, 5000, true);

    private static Path configFile() {
        return TestUtils.modFile("config.json");
    }

    private static int value(Config config, String option) {
        return GSON.toJsonTree(config).getAsJsonObject().get(option).getAsInt();
    }

    private static JsonObject readConfigFile() {
        return JsonParser.parseString(TestUtils.readFile(configFile())).getAsJsonObject();
    }

    /**
     * Runs {@code body} and always restores config.json afterwards, so the
     * changes never reach a later server start.
     */
    private static void restoringConfigFile(Runnable body) {
        String original = TestUtils.readFile(configFile());

        try {
            body.run();
        } finally {
            TestUtils.writeFile(configFile(), original);
        }
    }

    /**
     * A value below the minimum is raised to the minimum, in the loaded config and
     * in the file. A value at the minimum is left alone.
     */
    @GameTest
    public void valuesBelowMinimumAreCorrected(GameTestHelper helper) {
        restoringConfigFile(() -> {
            for (Map.Entry<String, Integer> entry : MINIMUMS.entrySet()) {
                String option = entry.getKey();
                int minimum = entry.getValue();

                TestUtils.writeFile(configFile(), GSON.toJson(configWith(option, minimum - 1)));
                assertEqual(helper, value(new Storage().getConfig(), option), minimum, "corrected \"" + option + "\"");
                assertEqual(helper, readConfigFile().get(option).getAsInt(), minimum, "corrected \"" + option + "\" in config.json");

                String atMinimum = GSON.toJson(configWith(option, minimum));
                TestUtils.writeFile(configFile(), atMinimum);
                assertEqual(helper, value(new Storage().getConfig(), option), minimum, "\"" + option + "\" at its minimum");
                assertEqual(helper, TestUtils.readFile(configFile()), atMinimum, "config.json with \"" + option + "\" at its minimum");
            }
        });
        helper.succeed();
    }

    /** Without config.json, one with the default values is created. */
    @GameTest
    public void missingConfigIsCreated(GameTestHelper helper) {
        restoringConfigFile(() -> {
            try {
                Files.delete(configFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            assertEqual(helper, new Storage().getConfig(), DEFAULTS, "config");
            assertEqual(helper, GSON.fromJson(readConfigFile(), Config.class), DEFAULTS, "config.json");
        });
        helper.succeed();
    }

    /**
     * Options missing in config.json, e.g. ones added by a newer version of the
     * mod, get their default values and are added to the file. The other options
     * are kept.
     */
    @GameTest
    public void missingOptionsAreAdded(GameTestHelper helper) {
        restoringConfigFile(() -> {
            TestUtils.writeFile(configFile(), "{\"maxHomes\": 3, \"enableTpa\": false}");

            Config expected = new Config("en_us", true, true, true, true, true, true, false, 3, 60, 5000, true);
            assertEqual(helper, new Storage().getConfig(), expected, "config");
            assertEqual(helper, GSON.fromJson(readConfigFile(), Config.class), expected, "config.json");
        });
        helper.succeed();
    }

    /** "defaultLanguage" selects the language file the messages are taken from. */
    @GameTest
    public void defaultLanguage(GameTestHelper helper) {
        restoringConfigFile(() -> {
            TestUtils.writeFile(configFile(), GSON.toJson(configWith("defaultLanguage", "de_de")));

            assertEqual(helper, new Storage().getTranslations(), TestUtils.readTranslations("de_de"), "translations");
        });
        helper.succeed();
    }
}

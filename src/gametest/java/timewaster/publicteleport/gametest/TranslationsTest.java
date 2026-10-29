package timewaster.publicteleport.gametest;

import static timewaster.publicteleport.gametest.TestUtils.assertEqual;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The language files in assets/public-teleport/lang. Every language needs
 * every message, otherwise players see the raw message key.
 */
public class TranslationsTest {
    /** All languages besides English, see the README. */
    private static final List<String> LANGUAGES = List.of("de_de");
    private static final Pattern PLACEHOLDER = Pattern.compile("%\\d+\\$s");

    /** The placeholders of a message, e.g. {@code [%1$s, %2$s]}. */
    private static Set<String> placeholders(String message) {
        Set<String> placeholders = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(message);

        while (matcher.find()) {
            placeholders.add(matcher.group());
        }

        return placeholders;
    }

    /**
     * Every language has the same messages as English, none of them empty, with
     * the same placeholders.
     */
    @GameTest
    public void languagesComplete(GameTestHelper helper) {
        Map<String, String> english = TestUtils.readTranslations("en_us");

        for (String language : LANGUAGES) {
            Map<String, String> translations = TestUtils.readTranslations(language);

            assertEqual(helper, new TreeSet<>(translations.keySet()), new TreeSet<>(english.keySet()), "message keys of " + language);
            english.forEach((key, message) -> {
                String translation = translations.get(key);

                helper.assertFalse(translation.isBlank(), "Expected \"" + key + "\" in " + language + " not to be empty");
                assertEqual(helper, placeholders(translation), placeholders(message), "placeholders of \"" + key + "\" in " + language);
            });
        }
        helper.succeed();
    }
}

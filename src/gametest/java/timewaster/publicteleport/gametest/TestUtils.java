package timewaster.publicteleport.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Blocks;
import timewaster.publicteleport.Messages;
import timewaster.publicteleport.Messages.MessageType;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.records.Config;
import timewaster.publicteleport.records.Teleport;

/**
 * Shared helpers for the game tests.
 */
public final class TestUtils {
    /** Where test players stand: one block above the floor built by {@link #buildFloor}. */
    public static final BlockPos STAND = new BlockPos(1, 1, 1);
    /** A second standing position, far enough from {@link #STAND} (more than 2 blocks) to teleport between. */
    public static final BlockPos STAND_FAR = new BlockPos(6, 1, 6);
    private static final String KEY_PREFIX = PublicTeleport.MOD_ID + ".";

    private TestUtils() {
    }

    /**
     * Builds the floor and spawns one player standing on it at {@link #STAND}.
     *
     * @param helper the test's helper
     * @return the player
     */
    public static TestPlayer setup(GameTestHelper helper) {
        buildFloor(helper);

        return TestPlayer.spawn(helper, STAND);
    }

    /**
     * Fills the bottom layer of the 8x8 test area with stone, so players have
     * something safe to stand on (at relative y = 1).
     *
     * @param helper the test's helper
     */
    public static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }

    /**
     * Returns a name no other test uses, for Warps and Portals, which are shared
     * between all players and therefore between tests running at the same time.
     *
     * @param prefix the start of the name
     * @return the name
     */
    public static String uniqueName(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Checks that the last message from this mod the player received is the one
     * with this identifier, color and parameters, i.e. what
     * {@code Messages.sendMessage(player, identifier, type, params)} sends. For
     * messages put together with {@link Messages.MessageBuilder} the first
     * translated part is checked.
     *
     * @param helper     the test's helper
     * @param player     the player who should have received the message
     * @param identifier the message identifier without the mod id prefix
     * @param type       the expected {@link MessageType}
     * @param params     the expected translation parameters
     */
    public static void assertLastMessage(GameTestHelper helper, TestPlayer player, String identifier, MessageType type,
        Object... params) {
        Component actual = translatedParts(lastModMessage(helper, player, identifier)).getFirst();
        Component expected = Messages.getMessage(identifier, type, params);
        TranslatableContents actualContents = (TranslatableContents) actual.getContents();

        assertEqual(helper, actualContents.getKey(), KEY_PREFIX + identifier, "message key");
        assertEqual(helper, actual.getStyle().getColor(), expected.getStyle().getColor(),
            "color of \"" + identifier + "\"");
        assertEqual(helper, toStrings(actualContents.getArgs()), toStrings(params),
            "parameters of \"" + identifier + "\"");
    }

    /**
     * Checks all translated parts of the last message from this mod, for
     * messages put together from several parts like the Homes list. Each part
     * is written as {@code identifier(param, ...)}, e.g.
     * {@code "button_named(base)"}.
     *
     * @param helper   the test's helper
     * @param player   the player who should have received the message
     * @param expected the expected parts in order
     */
    public static void assertLastMessageParts(GameTestHelper helper, TestPlayer player, String... expected) {
        Component message = lastModMessage(helper, player, expected[0]);
        List<String> actual = translatedParts(message).stream().map(part -> {
            TranslatableContents contents = (TranslatableContents) part.getContents();

            return contents.getKey().substring(KEY_PREFIX.length())
                + "(" + String.join(", ", toStrings(contents.getArgs())) + ")";
        }).toList();

        assertEqual(helper, actual, List.of(expected), "parts of the message");
    }

    /**
     * Checks that the player has received no message from this mod at all.
     *
     * @param helper the test's helper
     * @param player the player
     */
    public static void assertNoMessage(GameTestHelper helper, TestPlayer player) {
        List<Component> modMessages = modMessages(player);

        helper.assertTrue(modMessages.isEmpty(),
            "Expected no message, but got \"" + (modMessages.isEmpty() ? "" : modMessages.getLast().getString()) + "\"");
    }

    /**
     * Counts how many messages with this identifier the player received.
     *
     * @param player     the player
     * @param identifier the message identifier without the mod id prefix
     * @return the number of messages
     */
    public static long countMessages(TestPlayer player, String identifier) {
        return modMessages(player).stream()
            .filter(message -> ((TranslatableContents) translatedParts(message).getFirst().getContents()).getKey()
                .equals(KEY_PREFIX + identifier))
            .count();
    }

    /**
     * Returns the last message from this mod the player received.
     *
     * @param helper the test's helper
     * @param player the player
     * @return the message
     */
    public static Component lastModMessage(GameTestHelper helper, TestPlayer player) {
        return lastModMessage(helper, player, "a message");
    }

    private static Component lastModMessage(GameTestHelper helper, TestPlayer player, String expectedIdentifier) {
        List<Component> modMessages = modMessages(player);

        helper.assertFalse(modMessages.isEmpty(), "Expected \"" + expectedIdentifier + "\", but got no message");

        return modMessages.getLast();
    }

    /** Messages from this mod, without vanilla ones like "player joined the game" from other tests' players. */
    private static List<Component> modMessages(TestPlayer player) {
        return player.getMessages().stream().filter(message -> translatedParts(message).stream()
            .anyMatch(part -> ((TranslatableContents) part.getContents()).getKey().startsWith(KEY_PREFIX)))
            .toList();
    }

    /** All parts of a message that are translated, in reading order. */
    private static List<Component> translatedParts(Component message) {
        List<Component> parts = new ArrayList<>();

        if (message.getContents() instanceof TranslatableContents) {
            parts.add(message);
        }
        for (Component sibling : message.getSiblings()) {
            parts.addAll(translatedParts(sibling));
        }

        return parts;
    }

    /**
     * Fails the test if {@code actual} is not equal to {@code expected}. Used
     * instead of {@code GameTestHelper.assertValueEqual}, whose failure message
     * shows the two values the wrong way round and which fails on {@code null}.
     *
     * @param helper   the test's helper
     * @param actual   the value the mod produced
     * @param expected the value the test expects
     * @param name     what the value is, shown in the failure message
     */
    public static void assertEqual(GameTestHelper helper, Object actual, Object expected, String name) {
        if (!Objects.equals(actual, expected)) {
            throw helper.assertionException(
                Component.literal("Expected " + name + " to be " + expected + ", but was " + actual));
        }
    }

    /**
     * Converts an absolute position into one relative to the test area. Used
     * instead of {@code GameTestHelper.relativePos}, which in 26.1 turns the
     * result by 180 degrees (negates x and z) for tests that are not rotated.
     *
     * @param helper      the test's helper
     * @param absolutePos the absolute position
     * @return the position relative to the test area
     */
    public static BlockPos relative(GameTestHelper helper, BlockPos absolutePos) {
        return absolutePos.subtract(helper.absolutePos(BlockPos.ZERO));
    }

    /**
     * Waits until {@code asserter} stops failing, like
     * {@code GameTestHelper.succeedWhen}, but with a timeout in real seconds,
     * see {@link #withinSeconds}.
     *
     * @param helper   the test's helper
     * @param seconds  how long to wait at most
     * @param asserter the checks that have to pass
     */
    public static void succeedWithinSeconds(GameTestHelper helper, int seconds, Runnable asserter) {
        helper.succeedWhen(withinSeconds(helper, seconds, asserter));
    }

    /**
     * Wraps {@code asserter} for {@code GameTestSequence.thenWaitUntil}, so the
     * wait has a timeout in real seconds, counted from the first check. The
     * test server runs ticks as fast as it can, so a timeout in ticks says
     * nothing about how long work outside the tick loop, like loading chunks
     * or {@code /reload}, may take. Give the test a {@code maxTicks} high
     * enough to never be reached first.
     *
     * @param helper   the test's helper
     * @param seconds  how long to wait at most
     * @param asserter the checks that have to pass
     * @return the wrapped checks
     */
    public static Runnable withinSeconds(GameTestHelper helper, int seconds, Runnable asserter) {
        long[] deadline = { 0 };

        return () -> {
            if (deadline[0] == 0) {
                deadline[0] = System.nanoTime() + seconds * 1_000_000_000L;
            }
            try {
                asserter.run();
            } catch (GameTestAssertException e) {
                if (System.nanoTime() > deadline[0]) {
                    helper.fail(Component.literal("Not done after " + seconds + " seconds: " + e.getMessage()));
                }
                throw e;
            }
        };
    }

    /**
     * Runs {@code cleanup} when the test ends, whether it passed or failed.
     *
     * @param helper  the test's helper
     * @param cleanup what to do
     */
    public static void onTestEnd(GameTestHelper helper, Runnable cleanup) {
        GameTestInfo testInfo;

        try {
            // GameTestHelper has no getter for it
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            testInfo = (GameTestInfo) field.get(helper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("GameTestHelper.testInfo not found, did Minecraft rename it?", e);
        }

        testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {
            }

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                cleanup.run();
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                cleanup.run();
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
            }
        });
    }

    /**
     * Checks that a player stands in a specific block of the test area.
     *
     * @param helper      the test's helper
     * @param player      the player
     * @param relativePos the expected block, relative to the test area
     */
    public static void assertAt(GameTestHelper helper, TestPlayer player, BlockPos relativePos) {
        assertEqual(helper, relative(helper, player.blockPosition()), relativePos,
            "position of " + player.getName().getString());
    }

    /**
     * Checks that a stored Home or Warp points to a specific block of the test
     * area.
     *
     * @param helper      the test's helper
     * @param teleport    the stored Home or Warp
     * @param relativePos the expected block, relative to the test area
     */
    public static void assertTeleportAt(GameTestHelper helper, Teleport teleport, BlockPos relativePos) {
        assertEqual(helper, relative(helper, new BlockPos(teleport.x(), teleport.y(), teleport.z())), relativePos,
            "position of \"" + teleport.name() + "\"");
    }

    private static List<String> toStrings(Object... values) {
        return Arrays.stream(values).map(String::valueOf).toList();
    }

    /**
     * Runs {@code body} with a changed config and always restores the previous
     * config afterwards. All code in {@code body} runs in the same server tick,
     * so tests running at the same time never see the changed config.
     *
     * @param config the config to use while {@code body} runs
     * @param body   the test code
     */
    public static void withConfig(Config config, Runnable body) {
        Config previous = PublicTeleport.storage.getConfig();

        PublicTeleport.storage.setConfig(config);
        try {
            body.run();
        } finally {
            PublicTeleport.storage.setConfig(previous);
        }
    }

    /**
     * Returns a copy of the current config with one option changed.
     *
     * @param option the name of the option, as in config.json, e.g.
     *                   {@code "enableBack"}
     * @param value  the new value
     * @return the changed copy
     */
    public static Config configWith(String option, Object value) {
        Config config = Objects.requireNonNull(PublicTeleport.storage.getConfig());
        RecordComponent[] components = Config.class.getRecordComponents();
        Object[] values = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        boolean found = false;

        try {
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                if (components[i].getName().equals(option)) {
                    values[i] = value;
                    found = true;
                } else {
                    values[i] = components[i].getAccessor().invoke(config);
                }
            }

            if (!found) {
                throw new IllegalArgumentException("Config has no option \"" + option + "\"");
            }

            return Config.class.getDeclaredConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

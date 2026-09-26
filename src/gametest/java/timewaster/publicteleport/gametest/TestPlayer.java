package timewaster.publicteleport.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.phys.Vec3;

/**
 * A fake player that joins the test server like a real one and records every
 * chat message and dialog it receives, so tests can check what the mod told
 * the player.
 */
public final class TestPlayer extends ServerPlayer {
    private final List<Component> messages = new ArrayList<>();
    private Dialog lastDialog;
    private boolean hasLeft;

    private TestPlayer(MinecraftServer server, ServerLevel level, CommonListenerCookie cookie) {
        super(server, level, cookie.gameProfile(), cookie.clientInformation());
    }

    /**
     * Creates a fake player with a fresh UUID (so it has no Homes yet), adds it
     * to the server and puts it at a position inside the test area. The player
     * leaves the server automatically when the test ends, passed or failed.
     *
     * @param helper      the test's helper
     * @param relativePos the block to stand in, relative to the test area
     * @return the player
     */
    public static TestPlayer spawn(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        UUID uuid = UUID.randomUUID();
        String name = "test-" + uuid.toString().substring(0, 8);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(uuid, name), false);
        TestPlayer player = new TestPlayer(level.getServer(), level, cookie);
        // fully qualified: inside a ServerPlayer subclass "Connection" means WaypointTransmitter.Connection
        net.minecraft.network.Connection connection = new net.minecraft.network.Connection(PacketFlow.SERVERBOUND);

        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.moveTo(helper, relativePos);
        // without this, a test that fails halfway would leave its players on the server, where they would
        // receive e.g. /tpahereall requests of later tests
        TestUtils.onTestEnd(helper, player::leave);

        return player;
    }

    /**
     * Teleports the player to the middle of a block inside the test area,
     * facing south.
     *
     * @param helper      the test's helper
     * @param relativePos the block to stand in, relative to the test area
     */
    public void moveTo(GameTestHelper helper, BlockPos relativePos) {
        moveTo(helper, relativePos, 0, 0);
    }

    /**
     * Teleports the player to the middle of a block inside the test area.
     *
     * @param helper      the test's helper
     * @param relativePos the block to stand in, relative to the test area
     * @param yaw         the direction to face (0 = south, 90 = west, 180 = north,
     *                        -90 = east)
     * @param pitch       how far to look down (90) or up (-90)
     */
    public void moveTo(GameTestHelper helper, BlockPos relativePos, float yaw, float pitch) {
        moveTo(helper, new Vec3(relativePos.getX() + 0.5, relativePos.getY(), relativePos.getZ() + 0.5), yaw, pitch);
    }

    /**
     * Teleports the player to an exact position inside the test area.
     *
     * @param helper      the test's helper
     * @param relativePos the position, relative to the test area
     * @param yaw         the direction to face
     * @param pitch       how far to look down (90) or up (-90)
     */
    public void moveTo(GameTestHelper helper, Vec3 relativePos, float yaw, float pitch) {
        Vec3 pos = helper.absoluteVec(relativePos);

        teleportTo(helper.getLevel(), pos.x(), pos.y(), pos.z(), Set.of(), yaw, pitch, true);
    }

    /**
     * Runs a command as this player, exactly like typing it into chat.
     *
     * @param command the command without the leading slash
     */
    public void run(String command) {
        level().getServer().getCommands().performPrefixedCommand(createCommandSourceStack(), command);
    }

    /**
     * Runs a command as this player with owner (OP level 4) permissions.
     *
     * @param command the command without the leading slash
     */
    public void runAsOwner(String command) {
        level().getServer().getCommands().performPrefixedCommand(
            createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), command);
    }

    /**
     * Removes the player from the server, like disconnecting. Happens
     * automatically at the end of the test, but can be called earlier to test
     * what happens when a player goes offline.
     */
    public void leave() {
        if (!hasLeft) {
            hasLeft = true;
            level().getServer().getPlayerList().remove(this);
        }
    }

    @Override
    public void sendSystemMessage(Component message, boolean overlay) {
        messages.add(message);
        super.sendSystemMessage(message, overlay);
    }

    @Override
    public void openDialog(Holder<Dialog> dialog) {
        lastDialog = dialog.value();
        super.openDialog(dialog);
    }

    /**
     * Returns all messages the player received, including vanilla ones like
     * "player joined the game".
     *
     * @return the messages, oldest first
     */
    public List<Component> getMessages() {
        return messages;
    }

    public Dialog getLastDialog() {
        return lastDialog;
    }
}

package timewaster.publicteleport.commands;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import timewaster.publicteleport.PublicTeleport;
import timewaster.publicteleport.records.Config;

/**
 * Registers the mod's Brigadier commands and provides shared helpers used by
 * the individual command classes to build their argument nodes.
 */
public class Registrar {
    public static enum SuggestionType {
        NONE, HOMES, WARPS, PLAYERS
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        return context.getSource().getPlayer();
    }

    private static CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder, SuggestionType type) {
        ServerPlayer player = getPlayer(context);

        if (type != SuggestionType.NONE) {
            if (type == SuggestionType.HOMES || type == SuggestionType.WARPS) {
                List<String> teleportNames = PublicTeleport.storage.getTeleportNames(player,
                    type == SuggestionType.WARPS);

                if (teleportNames != null) {
                    for (String name : teleportNames) {
                        builder.suggest(name);
                    }
                }
            }

            if (type == SuggestionType.PLAYERS) {
                for (ServerPlayer activePlayer : context.getSource().getServer().getPlayerList().getPlayers()) {
                    if (!player.getUUID().equals(activePlayer.getUUID())) {
                        builder.suggest(activePlayer.getName().getString());
                    }
                }
            }
        }

        return builder.buildFuture();
    }

    /**
     * This is only for internal manual testing together with the mod
     * https://github.com/senseiwells/PuppetPlayers and is only active when the
     * coresponding JVM argument "PuppetMaster" is set.
     * I.e.: "java -Xmx2G -DPuppetMaster=1 -jar fabric-server-..."
     */
    private static void puppets(CommandDispatcher<CommandSourceStack> dispatcher) {
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();

        if (args.contains("-DPuppetMaster=1")) {
            dispatcher.register(Commands.literal("addpuppets")
                .executes(context -> Registrar.contextWrapper(context, (ServerPlayer player) -> {

                    for (int i = 0; i < 5; i++) {
                        player.level().getServer().getCommands().performPrefixedCommand(
                            player.createCommandSourceStack(),
                            "/puppet " + ((Double) Math.random()).toString().substring(2, 12) + " spawn");
                    }

                    return true;
                })));
        }
    }

    /**
     * Registers the mod's commands with Fabric's command dispatcher.
     */
    public static void registerCommands() {
        Config config = PublicTeleport.storage.getConfig();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            puppets(dispatcher);
            Help.register(dispatcher, config);

            if (config.enableSpawn()) {
                Spawn.register(dispatcher);
            }

            if (config.enableWarps()) {
                Warps.register(dispatcher);
            }

            if (config.enableHomes()) {
                Homes.register(dispatcher);
            }

            if (config.enableBack()) {
                Back.register(dispatcher);
            }

            if (config.enableTpa() && FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
                Tpa.register(dispatcher);
            }
        });
    }

    /**
     * Adapts a player-only command callback to Brigadier's expected command
     * execution signature.
     *
     * @param context  the command context Brigadier passes to the executor
     * @param callback the command logic to run for the executing player
     * @return {@code 1} if {@code callback} returned {@code true}, otherwise
     *         {@code 0}
     */
    public static int contextWrapper(CommandContext<CommandSourceStack> context,
        Function<ServerPlayer, Boolean> callback) {
        ServerPlayer player = getPlayer(context);

        return callback.apply(player) ? 1 : 0;
    }

    /**
     * Builds a {@code String} command argument, complete with tab-completion
     * suggestions and an executor.
     *
     * @param argName        the name of the argument node, and the key used to
     *                           read it back from the command context
     * @param suggestionType the type of tab-completion suggestions to offer for
     *                           this argument
     * @param callback       the command logic to run, given the executing
     *                           player and the argument's string value
     * @return the built argument node, ready to be attached under a literal
     *         command node
     */
    public static RequiredArgumentBuilder<CommandSourceStack, String> buildArgumentString(@NotNull String argName,
        SuggestionType suggestionType, BiFunction<ServerPlayer, String, Boolean> callback) {
        return Commands.argument(argName, Objects.requireNonNull(StringArgumentType.word()))
            .suggests((context, builder) -> getSuggestions(context, builder, suggestionType))
            .executes(context -> {
                ServerPlayer player = getPlayer(context);
                String argValue = Objects.requireNonNull(StringArgumentType.getString(context, argName));

                return callback.apply(player, argValue) ? 1 : 0;
            });
    }

    /**
     * Builds a player-selector command argument, complete with tab-completion
     * suggestions and an executor.
     *
     * @param argName        the name of the argument node, and the key used to
     *                           read it back from the command context
     * @param suggestionType the type of tab-completion suggestions to offer for
     *                           this argument
     * @param callback       the command logic to run, given the executing
     *                           player and the resolved target player
     * @return the built argument node, ready to be attached under a literal
     *         command node
     */
    public static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> buildArgumentPlayer(
        @NotNull String argName, SuggestionType suggestionType,
        BiFunction<ServerPlayer, ServerPlayer, Boolean> callback) {
        return Commands.argument(argName, EntityArgument.player())
            .suggests((context, builder) -> getSuggestions(context, builder, suggestionType))
            .executes(context -> {
                ServerPlayer player = getPlayer(context);
                ServerPlayer target = EntityArgument.getPlayer(Objects.requireNonNull(context), argName);

                return callback.apply(player, target) ? 1 : 0;
            });
    }
}

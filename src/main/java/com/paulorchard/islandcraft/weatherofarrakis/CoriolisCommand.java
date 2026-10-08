package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.joml.Vector3d;

import java.time.Duration;
import java.util.Locale;

/**
 * /coriolis status | start [approachSeconds] [stormSeconds] | stop | strike [x z].
 * No permission group is set, so only operators can run it.
 */
public class CoriolisCommand extends AbstractCommandCollection {

    private static final String LANG = "server.commands.coriolis.";

    public CoriolisCommand(CoriolisStormSystem system, CoriolisLightningSystem lightning) {
        super("coriolis", LANG + "desc");
        addSubCommand(new StatusCommand(system));
        addSubCommand(new StartCommand(system));
        addSubCommand(new StopCommand(system));
        addSubCommand(new StrikeCommand(lightning));
    }

    private static String number(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static Message disabled(World world) {
        return Message.translation(LANG + "disabled")
                .param("worldName", world.getName())
                .param("type", String.valueOf(CoriolisStormSystem.generatorType(world)));
    }

    private static class StatusCommand extends AbstractWorldCommand {

        private final CoriolisStormSystem system;

        StatusCommand(CoriolisStormSystem system) {
            super("status", LANG + "status.desc");
            this.system = system;
        }

        @Override
        protected void execute(CommandContext context, World world, Store<EntityStore> store) {
            if (!system.runsIn(world)) {
                context.sendMessage(disabled(world));
                return;
            }
            CoriolisStormResource state = CoriolisStorm.state(store);
            if (state.getPhase() != CoriolisPhase.CALM) {
                context.sendMessage(Message.translation(LANG + "status.phase")
                        .param("phase", state.getPhase().name())
                        .param("seconds", number(state.getPhaseRemainingSeconds(), 0)));
            }
            if (state.getPhase() == CoriolisPhase.APPROACH || state.getPhase() == CoriolisPhase.STORM) {
                // The next storm is only scheduled once this one ends.
                return;
            }
            if (state.getNextStormGameTime() == null) {
                context.sendMessage(Message.translation(LANG + "status.unscheduled"));
                return;
            }
            Duration wait = Duration.between(
                    store.getResource(WorldTimeResource.getResourceType()).getGameTime(), state.getNextStormGameTime());
            context.sendMessage(Message.translation(LANG + "status.next")
                    .param("days", number(wait.toMillis() / 86_400_000.0, 2))
                    .param("minutes", number(CoriolisStorm.gameToRealSeconds(world, wait) / 60.0, 0)));
        }
    }

    private static void start(CoriolisStormSystem system, CommandContext context, World world,
                              Store<EntityStore> store, double approachSeconds, double stormSeconds) {
        if (!system.runsIn(world)) {
            context.sendMessage(disabled(world));
            return;
        }
        system.start(world, store, approachSeconds, stormSeconds);

        CoriolisStormResource state = CoriolisStorm.state(store);
        context.sendMessage(Message.translation(LANG + "start.done")
                .param("approach", number(state.approachSeconds, 0))
                .param("storm", number(state.stormSeconds, 0)));
    }

    /** /coriolis start, with the configured lengths. The shortened forms are usage variants. */
    private static class StartCommand extends AbstractWorldCommand {

        private final CoriolisStormSystem system;

        StartCommand(CoriolisStormSystem system) {
            super("start", LANG + "start.desc");
            this.system = system;
            addUsageVariant(new StartVariant(system, false));
            addUsageVariant(new StartVariant(system, true));
        }

        @Override
        protected void execute(CommandContext context, World world, Store<EntityStore> store) {
            start(system, context, world, store, 0.0, 0.0);
        }
    }

    /** /coriolis start approachSeconds [stormSeconds]. Variants have no name; the argument count picks one. */
    private static class StartVariant extends AbstractWorldCommand {

        private final CoriolisStormSystem system;
        private final RequiredArg<Double> approachArg;
        private final RequiredArg<Double> stormArg;

        StartVariant(CoriolisStormSystem system, boolean withStormSeconds) {
            super(LANG + "start.desc");
            this.system = system;
            approachArg = withRequiredArg("approachSeconds", LANG + "start.approachSeconds.desc", ArgTypes.DOUBLE);
            stormArg = withStormSeconds
                    ? withRequiredArg("stormSeconds", LANG + "start.stormSeconds.desc", ArgTypes.DOUBLE)
                    : null;
        }

        @Override
        protected void execute(CommandContext context, World world, Store<EntityStore> store) {
            start(system, context, world, store,
                    approachArg.get(context), stormArg != null ? stormArg.get(context) : 0.0);
        }
    }

    private static class StopCommand extends AbstractWorldCommand {

        private final CoriolisStormSystem system;

        StopCommand(CoriolisStormSystem system) {
            super("stop", LANG + "stop.desc");
            this.system = system;
        }

        @Override
        protected void execute(CommandContext context, World world, Store<EntityStore> store) {
            if (!system.runsIn(world)) {
                context.sendMessage(disabled(world));
                return;
            }
            context.sendMessage(Message.translation(LANG + (system.stop(world, store) ? "stop.done" : "stop.notActive")));
        }
    }

    private static void strike(CoriolisLightningSystem lightning, CommandContext context, World world,
                               Store<EntityStore> store, int x, int z) {
        boolean struck = lightning.strikeAt(world, store, x, z);
        context.sendMessage(Message.translation(LANG + (struck ? "strike.done" : "strike.notLoaded"))
                .param("x", x).param("z", z));
    }

    /** /coriolis strike: a lightning strike on the spot where the caller stands. For testing, in any phase. */
    private static class StrikeCommand extends AbstractPlayerCommand {

        private final CoriolisLightningSystem lightning;

        StrikeCommand(CoriolisLightningSystem lightning) {
            super("strike", LANG + "strike.desc");
            this.lightning = lightning;
            addUsageVariant(new StrikeAtCommand(lightning));
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef playerRef, World world) {
            Vector3d position = CoriolisEffects.position(store, playerRef);
            if (position != null) {
                strike(lightning, context, world, store, (int) Math.floor(position.x()), (int) Math.floor(position.z()));
            }
        }
    }

    /** /coriolis strike x z: a strike on the top block of that column. Works from the console too. */
    private static class StrikeAtCommand extends AbstractWorldCommand {

        private final CoriolisLightningSystem lightning;
        private final RequiredArg<Integer> xArg;
        private final RequiredArg<Integer> zArg;

        StrikeAtCommand(CoriolisLightningSystem lightning) {
            super(LANG + "strike.desc");
            this.lightning = lightning;
            xArg = withRequiredArg("x", LANG + "strike.x.desc", ArgTypes.INTEGER);
            zArg = withRequiredArg("z", LANG + "strike.z.desc", ArgTypes.INTEGER);
        }

        @Override
        protected void execute(CommandContext context, World world, Store<EntityStore> store) {
            strike(lightning, context, world, store, xArg.get(context), zArg.get(context));
        }
    }
}

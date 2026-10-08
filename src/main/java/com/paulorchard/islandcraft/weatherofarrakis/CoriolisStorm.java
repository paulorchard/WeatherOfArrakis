package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The one place other code asks about the storm: which phase a world is in, how long
 * that phase has left, and a hook that fires whenever the phase changes.
 *
 * <p>The query methods read world state, so call them on that world's thread.
 */
public final class CoriolisStorm {

    /** Called on the world's thread each time that world's storm changes phase. */
    @FunctionalInterface
    public interface PhaseListener {
        void onPhaseChange(World world, CoriolisPhase previous, CoriolisPhase current);
    }

    private static final List<PhaseListener> LISTENERS = new CopyOnWriteArrayList<>();

    private CoriolisStorm() {
    }

    public static void addPhaseListener(PhaseListener listener) {
        LISTENERS.add(listener);
    }

    public static void removePhaseListener(PhaseListener listener) {
        LISTENERS.remove(listener);
    }

    /** The world's current phase. Worlds the storm does not run in are always CALM. */
    public static CoriolisPhase getPhase(Store<EntityStore> store) {
        return state(store).getPhase();
    }

    /**
     * Real seconds left in the current phase. In CALM this is an estimate of the time
     * until APPROACH, because the schedule is kept in game time and day and night run at
     * different speeds. Returns infinity when no storm is scheduled.
     */
    public static double getSecondsRemaining(Store<EntityStore> store) {
        CoriolisStormResource state = state(store);
        if (state.getPhase() != CoriolisPhase.CALM) {
            return state.getPhaseRemainingSeconds();
        }
        Instant next = state.getNextStormGameTime();
        if (next == null) {
            return Double.POSITIVE_INFINITY;
        }
        Instant now = store.getResource(WorldTimeResource.getResourceType()).getGameTime();
        return Math.max(0.0, gameToRealSeconds(store.getExternalData().getWorld(), Duration.between(now, next)));
    }

    /** Converts a span of game time to real seconds, averaged over a whole day. */
    static double gameToRealSeconds(World world, Duration gameTime) {
        double realSecondsPerDay = world.getDaytimeDurationSeconds() + world.getNighttimeDurationSeconds();
        return gameTime.toMillis() / 1000.0 * realSecondsPerDay / WorldTimeResource.SECONDS_PER_DAY;
    }

    static CoriolisStormResource state(Store<EntityStore> store) {
        return store.getResource(WeatherOfArrakisPlugin.get().getStormResourceType());
    }

    static void firePhaseChange(World world, CoriolisPhase previous, CoriolisPhase current) {
        for (PhaseListener listener : LISTENERS) {
            listener.onPhaseChange(world, previous, current);
        }
    }
}

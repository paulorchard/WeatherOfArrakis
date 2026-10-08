package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.IWorldGenProvider;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Runs the storm cycle for every world: CALM, APPROACH, STORM, CLEARING and back.
 * It forces the staged weathers and sends the chat warnings. Damage, shelter and the
 * other storm effects hook in through {@link CoriolisStorm}.
 */
public class CoriolisStormSystem extends TickingSystem<EntityStore> {

    public static final String STORM_WEATHER = "Arrakis_Coriolis_Storm";
    private static final String APPROACH_WEATHER_PREFIX = "Arrakis_Coriolis_Approach_";

    /** Fraction of the approach still to run when each approach weather takes over. With 300 s: 5:00, 4:00, 3:00, 2:00, 1:00, 0:30. */
    private static final double[] STAGE_START_FRACTIONS = {1.0, 0.8, 0.6, 0.4, 0.2, 0.1};

    private static final long MILLIS_PER_GAME_DAY = 86_400_000L;
    private static final Color WARNING_COLOR = new Color(0xE8, 0x7B, 0x2E);
    private static final String LANG = "server.weatherOfArrakis.";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Supplier<WeatherOfArrakisConfig> config;
    // Weather ids already reported as missing, so the log gets one line and not one per tick.
    private final Set<String> missingWeathers = new HashSet<>();

    public CoriolisStormSystem(Supplier<WeatherOfArrakisConfig> config) {
        this.config = config;
    }

    @Override
    public void tick(float dt, int systemIndex, Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        CoriolisStormResource state = CoriolisStorm.state(store);

        if (!runsIn(world)) {
            // Covers a world whose generator was taken off the list while a storm was running.
            if (state.getPhase() != CoriolisPhase.CALM) {
                releaseWeather(world, store, 0.0);
                setPhase(world, state, CoriolisPhase.CALM);
            }
            return;
        }

        switch (state.getPhase()) {
            case CALM -> tickCalm(world, store, state);
            case APPROACH -> tickApproach(world, store, state, dt);
            case STORM -> tickStorm(world, store, state, dt);
            case CLEARING -> tickClearing(world, state, dt);
        }
    }

    /** True when this world's generator type is on the configured list. */
    public boolean runsIn(World world) {
        String type = generatorType(world);
        if (type == null) {
            return false;
        }
        for (String allowed : config.get().getGeneratorTypes()) {
            if (type.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    /** The world's generator type as written in its config ("Dunes_of_Arrakis", "Flat", ...), or null. */
    public static String generatorType(World world) {
        IWorldGenProvider provider = world.getWorldConfig().getWorldGenProvider();
        return provider != null ? IWorldGenProvider.CODEC.getIdFor(provider.getClass()) : null;
    }

    /** Begins APPROACH now, from any phase. A length of zero or less means "use the configured value". */
    public void start(World world, Store<EntityStore> store, double approachSeconds, double stormSeconds) {
        WeatherOfArrakisConfig cfg = config.get();
        CoriolisStormResource state = CoriolisStorm.state(store);
        beginApproach(world, store, state,
                approachSeconds > 0.0 ? approachSeconds : cfg.getApproachSeconds(),
                stormSeconds > 0.0 ? stormSeconds : randomStormSeconds(cfg));
    }

    /** Ends an approaching or running storm and schedules the next one. Returns false if none was active. */
    public boolean stop(World world, Store<EntityStore> store) {
        CoriolisStormResource state = CoriolisStorm.state(store);
        if (state.getPhase() != CoriolisPhase.APPROACH && state.getPhase() != CoriolisPhase.STORM) {
            return false;
        }
        beginClearing(world, store, state);
        return true;
    }

    /** Tells a player who has just arrived in the world what the storm is doing. */
    public void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        World world = player != null ? player.getWorld() : null;
        Ref<EntityStore> ref = event.getPlayerRef();
        if (world == null || ref == null) {
            return;
        }
        world.execute(() -> {
            if (!ref.isValid() || !runsIn(world)) {
                return;
            }
            Store<EntityStore> store = world.getEntityStore().getStore();
            CoriolisStormResource state = CoriolisStorm.state(store);
            Message message = switch (state.getPhase()) {
                case APPROACH -> warning("joinApproach").param("time", timeText(state.getPhaseRemainingSeconds()));
                case STORM -> warning("joinStorm");
                default -> null;
            };
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (message != null && playerRef != null) {
                playerRef.sendMessage(message);
            }
        });
    }

    private void tickCalm(World world, Store<EntityStore> store, CoriolisStormResource state) {
        WeatherOfArrakisConfig cfg = config.get();
        Instant now = gameTime(store);
        long maxWaitMillis = (long) (cfg.getStormIntervalMaxDays() * MILLIS_PER_GAME_DAY);

        // No schedule yet (new world), or the clock was set back so far that the storm is further off than the longest gap.
        if (state.nextStormGameTime == null
                || Duration.between(now, state.nextStormGameTime).toMillis() > maxWaitMillis) {
            schedule(world, state, now);
            return;
        }
        if (!now.isBefore(state.nextStormGameTime)) {
            beginApproach(world, store, state, cfg.getApproachSeconds(), randomStormSeconds(cfg));
        }
    }

    private void tickApproach(World world, Store<EntityStore> store, CoriolisStormResource state, double dt) {
        state.phaseElapsedSeconds += dt;
        double remaining = state.approachSeconds - state.phaseElapsedSeconds;
        if (remaining <= 0.0) {
            beginStorm(world, store, state);
            return;
        }

        int stage = stageAt(remaining / state.approachSeconds);
        forceWeather(world, store, APPROACH_WEATHER_PREFIX + (stage + 1), stageBlendSeconds(stage, state.approachSeconds));
        sendDueWarnings(world, state, remaining);
    }

    private void tickStorm(World world, Store<EntityStore> store, CoriolisStormResource state, double dt) {
        state.phaseElapsedSeconds += dt;
        if (state.phaseElapsedSeconds >= state.stormSeconds) {
            beginClearing(world, store, state);
            return;
        }
        // Re-asserted every tick: this is what restores the storm after a restart or a stray /weather reset.
        forceWeather(world, store, STORM_WEATHER, 0.0);
    }

    private void tickClearing(World world, CoriolisStormResource state, double dt) {
        state.phaseElapsedSeconds += dt;
        if (state.phaseElapsedSeconds >= state.clearingSeconds) {
            setPhase(world, state, CoriolisPhase.CALM);
        }
    }

    private void beginApproach(World world, Store<EntityStore> store, CoriolisStormResource state,
                               double approachSeconds, double stormSeconds) {
        state.approachSeconds = approachSeconds;
        state.stormSeconds = stormSeconds;
        state.clearingSeconds = config.get().getClearingSeconds();
        state.nextWarningIndex = 0;
        setPhase(world, state, CoriolisPhase.APPROACH);
        LOGGER.at(Level.INFO).log("Coriolis storm approaching world '%s': arrives in %.0f s, lasts %.0f s",
                world.getName(), approachSeconds, stormSeconds);
        // Zero time step: sends the first warning and forces stage 1 in the same tick.
        tickApproach(world, store, state, 0.0);
    }

    private void beginStorm(World world, Store<EntityStore> store, CoriolisStormResource state) {
        setPhase(world, state, CoriolisPhase.STORM);
        forceWeather(world, store, STORM_WEATHER, 0.0);
        broadcast(world, warning("arrival"));
    }

    private void beginClearing(World world, Store<EntityStore> store, CoriolisStormResource state) {
        state.clearingSeconds = config.get().getClearingSeconds();
        releaseWeather(world, store, state.clearingSeconds);
        schedule(world, state, gameTime(store));
        setPhase(world, state, CoriolisPhase.CLEARING);
        broadcast(world, warning("end"));
    }

    private void setPhase(World world, CoriolisStormResource state, CoriolisPhase phase) {
        CoriolisPhase previous = state.getPhase();
        state.phase = phase;
        state.phaseElapsedSeconds = 0.0;
        state.exposureTimer = 0.0;
        state.lightningTimer = 0.0;
        state.frontTimer = 0.0;
        state.exposure.clear();
        state.flashes.clear();
        if (previous != phase) {
            CoriolisStorm.firePhaseChange(world, previous, phase);
        }
    }

    private void schedule(World world, CoriolisStormResource state, Instant now) {
        WeatherOfArrakisConfig cfg = config.get();
        double min = cfg.getStormIntervalMinDays();
        double max = cfg.getStormIntervalMaxDays();
        double days = min + ThreadLocalRandom.current().nextDouble() * (max - min);
        state.nextStormGameTime = now.plusMillis((long) (days * MILLIS_PER_GAME_DAY));
        LOGGER.at(Level.INFO).log("Next Coriolis storm in world '%s' is %.2f in-game days away", world.getName(), days);
    }

    private static double randomStormSeconds(WeatherOfArrakisConfig cfg) {
        double min = cfg.getStormDurationMinSeconds();
        double max = cfg.getStormDurationMaxSeconds();
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    private static int stageAt(double fractionRemaining) {
        int stage = 0;
        while (stage + 1 < STAGE_START_FRACTIONS.length && fractionRemaining <= STAGE_START_FRACTIONS[stage + 1]) {
            stage++;
        }
        return stage;
    }

    /** How long the blend into an approach stage takes: a share of the time that stage lasts. */
    private double stageBlendSeconds(int stage, double approachSeconds) {
        double end = stage + 1 < STAGE_START_FRACTIONS.length ? STAGE_START_FRACTIONS[stage + 1] : 0.0;
        return (STAGE_START_FRACTIONS[stage] - end) * approachSeconds * config.get().getStageBlendFraction();
    }

    private void sendDueWarnings(World world, CoriolisStormResource state, double remaining) {
        WeatherOfArrakisConfig cfg = config.get();
        double[] times = cfg.getWarningSeconds();
        // A shortened approach squeezes the warnings to fit, so they keep their spacing.
        double scale = state.approachSeconds / cfg.getApproachSeconds();

        if (state.nextWarningIndex < 0) {
            // Resuming after a restart: skip the warnings that had already gone out.
            state.nextWarningIndex = 0;
            while (state.nextWarningIndex < times.length && remaining <= times[state.nextWarningIndex] * scale) {
                state.nextWarningIndex++;
            }
            return;
        }

        while (state.nextWarningIndex < times.length && remaining <= times[state.nextWarningIndex] * scale) {
            int index = state.nextWarningIndex++;
            String key = index == 0 ? "approach" : index == times.length - 1 ? "imminent" : "countdown";
            broadcast(world, warning(key).param("time", timeText(times[index] * scale)));
        }
    }

    /**
     * Forces a weather for the whole world. Does nothing if it is already forced.
     *
     * <p>The game blends a forced weather in over a fixed 10 seconds. It only sends a
     * player a weather they are not already on, so telling each player first, with our own
     * blend time, makes the game's send a no-op and our blend time stands.
     */
    private void forceWeather(World world, Store<EntityStore> store, String weatherId, double blendSeconds) {
        int index = weatherIndex(weatherId);
        if (index == Integer.MIN_VALUE) {
            return;
        }
        WeatherResource weather = store.getResource(WeatherResource.getResourceType());
        if (weather.getForcedWeatherIndex() == index) {
            return;
        }
        if (blendSeconds > 0.0) {
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                WeatherTracker tracker = tracker(store, playerRef);
                if (tracker != null) {
                    tracker.sendWeatherIndex(playerRef, index, (float) blendSeconds);
                }
            }
        }
        weather.setForcedWeather(weatherId);
    }

    /** Hands the weather back to the forecast, or to the world's own forced weather if it has one. */
    private void releaseWeather(World world, Store<EntityStore> store, double blendSeconds) {
        WeatherResource weather = store.getResource(WeatherResource.getResourceType());
        // Only the resource is touched during a storm, so the world config still holds whatever /weather set left there.
        String configured = world.getWorldConfig().getForcedWeather();
        int configuredIndex = configured != null ? weatherIndex(configured) : Integer.MIN_VALUE;

        if (blendSeconds > 0.0) {
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                WeatherTracker tracker = tracker(store, playerRef);
                if (tracker != null) {
                    int index = configuredIndex != Integer.MIN_VALUE
                            ? configuredIndex
                            : weather.getWeatherIndexForEnvironment(tracker.getEnvironmentId());
                    tracker.sendWeatherIndex(playerRef, index, (float) blendSeconds);
                }
            }
        }
        weather.setForcedWeather(configuredIndex != Integer.MIN_VALUE ? configured : null);
    }

    private int weatherIndex(String weatherId) {
        int index = Weather.getAssetMap().getIndex(weatherId);
        if (index == Integer.MIN_VALUE && missingWeathers.add(weatherId)) {
            LOGGER.at(Level.SEVERE).log("Weather '%s' is not loaded, so it cannot be shown", weatherId);
        }
        return index;
    }

    private static WeatherTracker tracker(Store<EntityStore> store, PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        return ref != null && ref.isValid() ? store.getComponent(ref, WeatherTracker.getComponentType()) : null;
    }

    private static Instant gameTime(Store<EntityStore> store) {
        return store.getResource(WorldTimeResource.getResourceType()).getGameTime();
    }

    private static void broadcast(World world, Message message) {
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            playerRef.sendMessage(message);
        }
    }

    private static Message warning(String key) {
        return Message.translation(LANG + "coriolis." + key).color(WARNING_COLOR);
    }

    /** "5 minutes", "30 seconds", or "3 min 37 s" when it is not a round number of minutes. */
    static Message timeText(double seconds) {
        int total = Math.max(1, (int) Math.round(seconds));
        if (total < 60) {
            return Message.translation(LANG + "time.seconds").param("count", total);
        }
        if (total % 60 == 0) {
            return Message.translation(LANG + "time.minutes").param("count", total / 60);
        }
        return Message.translation(LANG + "time.minutesSeconds").param("minutes", total / 60).param("seconds", total % 60);
    }
}

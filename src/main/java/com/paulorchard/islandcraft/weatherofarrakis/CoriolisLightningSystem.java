package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Lightning during STORM: a bolt, thunder, a flash and a camera jolt every few seconds,
 * landing somewhere near each group of players. It is not aimed, but an exposed player
 * who happens to be next to a strike is hurt. It changes no blocks.
 */
public class CoriolisLightningSystem extends TickingSystem<EntityStore> implements CoriolisStorm.PhaseListener {

    private static final String BOLT_PARTICLES = "Arrakis_Coriolis_Lightning";
    private static final String THUNDER_SOUND = "Arrakis_SFX_Coriolis_Thunder";
    private static final String FLASH_WEATHER = "Arrakis_Coriolis_Flash";

    /** Players this close together share one strike. */
    private static final double GROUP_RADIUS = 96.0;
    /** The bolt is sent to players within this distance; the particle system culls at the same range. */
    private static final double VIEW_RADIUS = 300.0;
    private static final double FLASH_RADIUS = 96.0;
    private static final double FLASH_SECONDS = 0.15;
    private static final float FLASH_IN_SECONDS = 0.05f;
    private static final float FLASH_OUT_SECONDS = 0.4f;

    private final Supplier<WeatherOfArrakisConfig> config;

    public CoriolisLightningSystem(Supplier<WeatherOfArrakisConfig> config) {
        this.config = config;
    }

    @Override
    public void tick(float dt, int systemIndex, Store<EntityStore> store) {
        CoriolisStormResource state = CoriolisStorm.state(store);
        if (state.getPhase() != CoriolisPhase.STORM) {
            return;
        }
        World world = store.getExternalData().getWorld();
        endFinishedFlashes(world, store, state, dt);

        state.lightningTimer -= dt;
        if (state.lightningTimer > 0.0) {
            return;
        }
        WeatherOfArrakisConfig cfg = config.get();
        double min = Math.min(cfg.getLightningMinSeconds(), cfg.getLightningMaxSeconds());
        double max = Math.max(cfg.getLightningMinSeconds(), cfg.getLightningMaxSeconds());
        // The floor stops a zero in the config from striking every tick.
        state.lightningTimer = Math.max(0.25, min + ThreadLocalRandom.current().nextDouble() * (max - min));

        for (Vector3d target : pickOnePlayerPerGroup(world, store)) {
            strikeNear(world, store, target, cfg);
        }
    }

    /** A flash must not outlive the storm, so drop any that are running when the phase changes. */
    @Override
    public void onPhaseChange(World world, CoriolisPhase previous, CoriolisPhase current) {
        if (previous != CoriolisPhase.STORM) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            WeatherTracker tracker = tracker(store, playerRef);
            if (tracker != null) {
                // The storm system releases or re-forces the weather next; it will send the right one.
                tracker.clearOverrideWeatherIndex();
            }
        }
    }

    /** Strikes the top block of the column at (x, z). Returns false if that column is not loaded. */
    public boolean strikeAt(World world, Store<EntityStore> store, int x, int z) {
        WorldBlocks blocks = new WorldBlocks(world);
        if (blocks.chunkAt(x, z) == null) {
            return false;
        }
        strike(world, store, x + 0.5, blocks.topBlockY(x, z) + 1.0, z + 0.5, config.get());
        return true;
    }

    private void strikeNear(World world, Store<EntityStore> store, Vector3d player, WeatherOfArrakisConfig cfg) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double min = Math.min(cfg.getLightningMinDistance(), cfg.getLightningMaxDistance());
        double max = Math.max(cfg.getLightningMinDistance(), cfg.getLightningMaxDistance());
        double distance = min + random.nextDouble() * (max - min);
        double angle = random.nextDouble() * Math.PI * 2.0;
        strikeAt(world, store,
                (int) Math.floor(player.x() + Math.cos(angle) * distance),
                (int) Math.floor(player.z() + Math.sin(angle) * distance));
    }

    private void strike(World world, Store<EntityStore> store, double x, double y, double z, WeatherOfArrakisConfig cfg) {
        List<Ref<EntityStore>> viewers = new ArrayList<>();
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Vector3d position = CoriolisEffects.position(store, playerRef);
            if (position == null) {
                continue;
            }
            double distance = position.distance(x, y, z);
            if (distance <= VIEW_RADIUS) {
                viewers.add(playerRef.getReference());
            }
            if (distance <= cfg.getLightningShakeRadius()) {
                CoriolisEffects.shake(playerRef, CoriolisEffects.LIGHTNING_SHAKE,
                        cfg.getLightningShakeIntensity() * (1.0 - distance / cfg.getLightningShakeRadius()));
            }
            if (cfg.isLightningFlash() && distance <= FLASH_RADIUS) {
                startFlash(store, playerRef);
            }
            if (cfg.getLightningDamage() > 0.0 && distance <= cfg.getLightningDamageRadius()) {
                hurtIfExposed(world, store, playerRef, position, cfg);
            }
        }

        if (!viewers.isEmpty()) {
            ParticleUtil.spawnParticleEffect(BOLT_PARTICLES, x, y, z, viewers, store);
        }
        int thunder = SoundEvent.getAssetMap().getIndex(THUNDER_SOUND);
        if (thunder != Integer.MIN_VALUE) {
            SoundUtil.playSoundEvent3d(thunder, SoundCategory.SFX, x, y, z, store);
        }
    }

    private static void hurtIfExposed(World world, Store<EntityStore> store, PlayerRef playerRef, Vector3d position,
                                      WeatherOfArrakisConfig cfg) {
        Ref<EntityStore> ref = playerRef.getReference();
        Player player = store.getComponent(ref, Player.getComponentType());
        DamageCause cause = CoriolisStormDamage.cause();
        if (player == null || cause == null || player.getGameMode() == GameMode.Creative
                || store.getComponent(ref, DeathComponent.getComponentType()) != null
                || CoriolisExposureSystem.verdictAt(world, position, cfg).sheltered) {
            return;
        }
        float amount = (float) cfg.getLightningDamage();
        // Run after this tick's systems have finished, the same as a command would.
        world.execute(() -> {
            if (ref.isValid()) {
                DamageSystems.executeDamage(ref, store, new Damage(CoriolisStormDamage.SOURCE, cause, amount));
            }
        });
    }

    /**
     * Switches one player to the bright copy of the storm weather for a moment. The override
     * keeps the game's once-a-second weather send from undoing it half way through.
     */
    private static void startFlash(Store<EntityStore> store, PlayerRef playerRef) {
        int flash = Weather.getAssetMap().getIndex(FLASH_WEATHER);
        WeatherTracker tracker = tracker(store, playerRef);
        if (flash == Integer.MIN_VALUE || tracker == null) {
            return;
        }
        tracker.setOverrideWeatherIndex(flash);
        tracker.sendWeatherIndex(playerRef, flash, FLASH_IN_SECONDS);
        CoriolisStorm.state(store).flashes.put(playerRef.getUuid(), FLASH_SECONDS);
    }

    private static void endFinishedFlashes(World world, Store<EntityStore> store, CoriolisStormResource state, double dt) {
        if (state.flashes.isEmpty()) {
            return;
        }
        int storm = Weather.getAssetMap().getIndex(CoriolisStormSystem.STORM_WEATHER);
        for (Iterator<Map.Entry<UUID, Double>> it = state.flashes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Double> flash = it.next();
            flash.setValue(flash.getValue() - dt);
            if (flash.getValue() > 0.0) {
                continue;
            }
            it.remove();
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                WeatherTracker tracker = playerRef.getUuid().equals(flash.getKey()) ? tracker(store, playerRef) : null;
                if (tracker != null) {
                    tracker.clearOverrideWeatherIndex();
                    tracker.sendWeatherIndex(playerRef, storm, FLASH_OUT_SECONDS);
                }
            }
        }
    }

    /** One position per cluster of players: a random member of each. */
    private static List<Vector3d> pickOnePlayerPerGroup(World world, Store<EntityStore> store) {
        List<Vector3d> remaining = new ArrayList<>();
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Vector3d position = CoriolisEffects.position(store, playerRef);
            if (position != null) {
                remaining.add(position);
            }
        }
        List<Vector3d> picks = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Vector3d seed = remaining.get(0);
            List<Vector3d> group = new ArrayList<>();
            for (Iterator<Vector3d> it = remaining.iterator(); it.hasNext(); ) {
                Vector3d other = it.next();
                if (other.distance(seed) <= GROUP_RADIUS) {
                    group.add(other);
                    it.remove();
                }
            }
            picks.add(group.get(ThreadLocalRandom.current().nextInt(group.size())));
        }
        return picks;
    }

    private static WeatherTracker tracker(Store<EntityStore> store, PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        return ref != null && ref.isValid() ? store.getComponent(ref, WeatherTracker.getComponentType()) : null;
    }
}

package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.List;
import java.util.function.Supplier;

/**
 * Experimental storm front: during APPROACH, a wall of sand particles upwind of each player
 * that closes in as the clock runs down. Off unless StormFront is set in the config.
 *
 * <p>The wall is drawn for each player separately, at a distance that depends only on the
 * time left, so two players standing apart both see it arrive at the same moment.
 */
public class CoriolisFrontSystem extends TickingSystem<EntityStore> {

    private static final String FRONT_PARTICLES = "Arrakis_Coriolis_Front";
    /** The particles live about 5 seconds, so drawing again this often keeps the wall solid. */
    private static final double REDRAW_SECONDS = 2.5;
    /** Start the wall below the player's feet so no gap shows under it on a slope. */
    private static final double BASE_DROP = 8.0;

    private final Supplier<WeatherOfArrakisConfig> config;

    public CoriolisFrontSystem(Supplier<WeatherOfArrakisConfig> config) {
        this.config = config;
    }

    @Override
    public void tick(float dt, int systemIndex, Store<EntityStore> store) {
        WeatherOfArrakisConfig cfg = config.get();
        CoriolisStormResource state = CoriolisStorm.state(store);
        if (!cfg.isStormFront() || state.getPhase() != CoriolisPhase.APPROACH) {
            return;
        }
        state.frontTimer -= dt;
        if (state.frontTimer > 0.0) {
            return;
        }
        state.frontTimer = REDRAW_SECONDS;

        WindDirection wind = cfg.getWindFrom();
        double distance = cfg.getStormFrontDistance() * state.getPhaseRemainingSeconds() / state.getPhaseLengthSeconds();
        // The particle system's long side is Z. Turn it a quarter when the wind is along Z.
        Rotation3f rotation = new Rotation3f(0f, wind.upwindZ != 0 ? (float) (Math.PI / 2.0) : 0f, 0f);

        World world = store.getExternalData().getWorld();
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Vector3d position = CoriolisEffects.position(store, playerRef);
            if (position == null) {
                continue;
            }
            Vector3d wall = new Vector3d(
                    position.x() + wind.upwindX * distance, position.y() - BASE_DROP, position.z() + wind.upwindZ * distance);
            List<Ref<EntityStore>> onlyThisPlayer = List.of(playerRef.getReference());
            ParticleUtil.spawnParticleEffect(FRONT_PARTICLES, wall, rotation, onlyThisPlayer, store);
        }
    }
}

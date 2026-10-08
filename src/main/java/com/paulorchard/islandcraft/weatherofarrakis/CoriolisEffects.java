package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.builtin.adventure.camera.asset.camerashake.CameraShake;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AccumulationMode;
import com.hypixel.hytale.protocol.packets.camera.CameraShakeEffect;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/** Small helpers shared by the storm's sights and sounds. */
final class CoriolisEffects {

    static final String ARRIVAL_SHAKE = "Arrakis_Coriolis_Arrival";
    static final String LIGHTNING_SHAKE = "Arrakis_Coriolis_Lightning";
    static final String TREMBLE_SHAKE = "Arrakis_Coriolis_Tremble";

    private CoriolisEffects() {
    }

    /**
     * Plays one of this mod's camera shakes for a player. The packet is built here, not through
     * a CameraEffect asset, so the strength can come straight from the config.
     */
    static void shake(PlayerRef playerRef, String shakeId, double intensity) {
        if (intensity <= 0.0) {
            return;
        }
        int index = CameraShake.getAssetMap().getIndex(shakeId);
        if (index != Integer.MIN_VALUE) {
            playerRef.getPacketHandler().writeNoCache(new CameraShakeEffect(index, (float) intensity, AccumulationMode.Set));
        }
    }

    /** A player's position, or null if they are not in the world right now. */
    static Vector3d position(Store<EntityStore> store, PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        return transform != null ? transform.getPosition() : null;
    }

    /** The shake every player gets as the storm arrives: full strength in the open, half in shelter. */
    static void arrivalShake(World world, WeatherOfArrakisConfig cfg) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Vector3d position = position(store, playerRef);
            if (position != null) {
                boolean sheltered = CoriolisExposureSystem.verdictAt(world, position, cfg).sheltered;
                shake(playerRef, ARRIVAL_SHAKE, cfg.getArrivalShakeIntensity() * (sheltered ? 0.5 : 1.0));
            }
        }
    }
}

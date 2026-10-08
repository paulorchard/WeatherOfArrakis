package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The storm as a source of damage. Other code can recognise storm damage by its cause,
 * {@link #CAUSE_ID}, or by {@code damage.getSource() instanceof CoriolisStormDamage}.
 */
public final class CoriolisStormDamage implements Damage.Source {

    /** Damage cause asset: no armour or resistance reduction, no vanilla durability loss. */
    public static final String CAUSE_ID = "Arrakis_Coriolis_Storm";

    public static final CoriolisStormDamage SOURCE = new CoriolisStormDamage();

    private CoriolisStormDamage() {
    }

    /** The cause asset, or null if the asset pack did not load. */
    public static DamageCause cause() {
        return DamageCause.getAssetMap().getAsset(CAUSE_ID);
    }

    @Override
    public Message getDeathMessage(Damage damage, Ref<EntityStore> victim, ComponentAccessor<EntityStore> accessor) {
        PlayerRef player = accessor.getComponent(victim, PlayerRef.getComponentType());
        return Message.translation("server.weatherOfArrakis.coriolis.death")
                .param("player", player != null ? player.getUsername() : "?");
    }
}

package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ActiveSlotInventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.awt.Color;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Once a second during STORM, works out which players are in the open, wears down the
 * gear of those who are, and hurts them. Players in shelter, in Creative mode or already
 * dead are left alone.
 */
public class CoriolisExposureSystem extends EntityTickingSystem<EntityStore> {

    private static final double CHECK_INTERVAL_SECONDS = 1.0;
    private static final String BREAK_SOUND = "SFX_Item_Break";
    private static final Color WARNING_COLOR = new Color(0xE8, 0x7B, 0x2E);
    private static final String LANG = "server.weatherOfArrakis.exposure.";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Supplier<WeatherOfArrakisConfig> config;
    private final Query<EntityStore> query = Archetype.of(
            Player.getComponentType(), PlayerRef.getComponentType(), TransformComponent.getComponentType());
    private boolean missingCauseReported;

    public CoriolisExposureSystem(Supplier<WeatherOfArrakisConfig> config) {
        this.config = config;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    /** Runs for the whole world each tick. Lets the per-player pass through once a second, and only during STORM. */
    @Override
    public void tick(float dt, int systemIndex, Store<EntityStore> store) {
        CoriolisStormResource state = CoriolisStorm.state(store);
        if (state.getPhase() != CoriolisPhase.STORM) {
            return;
        }
        state.exposureTimer += dt;
        if (state.exposureTimer < CHECK_INTERVAL_SECONDS) {
            return;
        }
        // Keep the remainder so checks stay one second apart, but never queue up more than one.
        state.exposureTimer = Math.min(state.exposureTimer - CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS);
        store.tick(this, dt, systemIndex);
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        Player player = chunk.getComponent(index, Player.getComponentType());
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (player == null || playerRef == null || transform == null
                || player.getGameMode() == GameMode.Creative
                || chunk.getArchetype().contains(DeathComponent.getComponentType())) {
            return;
        }

        WeatherOfArrakisConfig cfg = config.get();
        World world = store.getExternalData().getWorld();
        CoriolisStormResource.PlayerExposure exposure = CoriolisStorm.state(store).exposure
                .computeIfAbsent(playerRef.getUuid(), uuid -> new CoriolisStormResource.PlayerExposure());

        Vector3d position = transform.getPosition();
        // The small lift keeps a player standing a hair inside the ground from counting as one block lower.
        StormShelter.Verdict verdict = StormShelter.check(new WorldBlocks(world),
                (int) Math.floor(position.x()), (int) Math.floor(position.y() + 0.1), (int) Math.floor(position.z()),
                cfg.getWindFrom(), cfg.getDeepCoverSkyLight(), cfg.getRoofBlocks(), cfg.getWindbreakBlocks());

        if (verdict.sheltered) {
            exposure.exposedChecks = 0;
            tell(playerRef, exposure, false);
            return;
        }

        exposure.exposedChecks++;
        tell(playerRef, exposure, true);
        if (exposure.exposedChecks <= cfg.getExposureGraceChecks()) {
            return;
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        boolean armoured = scourGear(store, ref, playerRef, cfg.getDurabilityLossPerSecond());
        hurt(ref, commandBuffer, armoured ? cfg.getHealthLossArmoured() : cfg.getHealthLossStripped());
    }

    /** Announces a change between exposed and sheltered, and the first state of the storm. */
    private static void tell(PlayerRef playerRef, CoriolisStormResource.PlayerExposure exposure, boolean exposed) {
        if (exposure.toldExposed != null && exposure.toldExposed == exposed) {
            return;
        }
        exposure.toldExposed = exposed;
        playerRef.sendMessage(Message.translation(LANG + (exposed ? "exposed" : "sheltered")).color(WARNING_COLOR));
    }

    /**
     * Wears down every armour piece, the held item and the active off-hand item.
     * Returns true if the player still wears at least one armour piece that has durability.
     */
    private static boolean scourGear(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, double loss) {
        boolean armoured = false;
        InventoryComponent.Armor armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        if (armor != null) {
            ItemContainer pieces = armor.getInventory();
            for (short slot = 0; slot < pieces.getCapacity(); slot++) {
                armoured |= scour(pieces, slot, playerRef, loss);
            }
        }
        // The hand holds either the active tool-belt item or the active hotbar item.
        InventoryComponent.Tool tools = store.getComponent(ref, InventoryComponent.Tool.getComponentType());
        scourActive(tools != null && tools.isUsingToolsItem()
                ? tools : store.getComponent(ref, InventoryComponent.Hotbar.getComponentType()), playerRef, loss);
        scourActive(store.getComponent(ref, InventoryComponent.Utility.getComponentType()), playerRef, loss);
        return armoured;
    }

    private static void scourActive(ActiveSlotInventoryComponent section, PlayerRef playerRef, double loss) {
        if (section != null) {
            scour(section.getInventory(), section.getActiveSlot(), playerRef, loss);
        }
    }

    /** Wears down the item in one slot. Returns true if an item with durability is still there afterwards. */
    private static boolean scour(ItemContainer container, short slot, PlayerRef playerRef, double loss) {
        if (container == null || slot < 0 || slot >= container.getCapacity()) {
            return false;
        }
        ItemStack stack = container.getItemStack(slot);
        if (ItemStack.isEmpty(stack) || stack.getMaxDurability() <= 0.0 || stack.isUnbreakable()) {
            return false;
        }
        double durability = stack.getDurability() - loss;
        if (durability > 0.0) {
            container.setItemStackForSlot(slot, stack.withDurability(durability));
            return true;
        }

        // Vanilla would leave the item in place as "broken". The storm takes it.
        container.removeItemStackFromSlot(slot);
        playerRef.sendMessage(Message.translation(LANG + "tornAway")
                .param("item", Message.translation(stack.getItem().getTranslationKey()))
                .color(WARNING_COLOR));
        int sound = SoundEvent.getAssetMap().getIndex(BREAK_SOUND);
        if (sound != Integer.MIN_VALUE) {
            SoundUtil.playSoundEvent2dToPlayer(playerRef, sound, SoundCategory.SFX);
        }
        return false;
    }

    /** Deals the health loss the same way vanilla deals drowning: a Damage event with no attacker. */
    private void hurt(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer, double amount) {
        if (amount <= 0.0) {
            return;
        }
        DamageCause cause = CoriolisStormDamage.cause();
        if (cause == null) {
            if (!missingCauseReported) {
                missingCauseReported = true;
                LOGGER.at(Level.SEVERE).log("Damage cause '%s' is not loaded, so the storm cannot hurt anyone",
                        CoriolisStormDamage.CAUSE_ID);
            }
            return;
        }
        DamageSystems.executeDamage(ref, commandBuffer, new Damage(CoriolisStormDamage.SOURCE, cause, (float) amount));
    }
}

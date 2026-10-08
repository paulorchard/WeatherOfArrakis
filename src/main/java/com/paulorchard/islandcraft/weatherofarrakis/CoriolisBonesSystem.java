package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.VariantRotation;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Leaves a bone block where a player dies while the storm is overhead, whatever killed them. */
public class CoriolisBonesSystem extends DeathSystems.OnDeathSystem {

    private static final int MAX_DROP_BLOCKS = 8;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Supplier<WeatherOfArrakisConfig> config;
    private final Query<EntityStore> query = Archetype.of(Player.getComponentType());

    public CoriolisBonesSystem(Supplier<WeatherOfArrakisConfig> config) {
        this.config = config;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death, Store<EntityStore> store,
                                 CommandBuffer<EntityStore> commandBuffer) {
        if (CoriolisStorm.getPhase(store) != CoriolisPhase.STORM) {
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        String blockId = config.get().getBoneBlock();
        if (transform == null || blockId.isEmpty()) {
            return;
        }
        Vector3d position = transform.getPosition();
        int x = (int) Math.floor(position.x());
        int y = (int) Math.floor(position.y() + 0.1);
        int z = (int) Math.floor(position.z());

        // Blocks are changed after this system has finished, not in the middle of the death handling.
        World world = store.getExternalData().getWorld();
        world.execute(() -> placeBones(world, blockId, x, y, z));
    }

    private static void placeBones(World world, String blockId, int x, int y, int z) {
        int id = BlockType.getAssetMap().getIndex(blockId);
        BlockType type = id != Integer.MIN_VALUE ? BlockType.getAssetMap().getAsset(id) : null;
        if (type == null) {
            LOGGER.at(Level.WARNING).log("Bone block '%s' does not exist; no bones placed", blockId);
            return;
        }
        WorldBlocks blocks = new WorldBlocks(world);

        // The death position, then straight down, then the eight blocks around it.
        for (int drop = 0; drop <= MAX_DROP_BLOCKS; drop++) {
            if (tryPlace(blocks, id, type, x, y - drop, z)) {
                return;
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && tryPlace(blocks, id, type, x + dx, y, z + dz)) {
                    return;
                }
            }
        }
    }

    /** Places the block only in an empty space with solid ground under it. Never replaces anything. */
    private static boolean tryPlace(WorldBlocks blocks, int id, BlockType type, int x, int y, int z) {
        if (!blocks.empty(x, y, z) || !blocks.solid(x, y - 1, z)) {
            return false;
        }
        WorldChunk chunk = blocks.chunkAt(x, z);
        // Blocks with no rotation variants ignore this; their look is varied by the client instead.
        int rotation = type.getVariantRotation() == VariantRotation.None ? RotationTuple.NONE_INDEX
                : RotationTuple.index(Rotation.NORMAL[ThreadLocalRandom.current().nextInt(Rotation.NORMAL.length)], Rotation.None, Rotation.None);
        return chunk.setBlock(x, y, z, id, type, rotation, 0, 0);
    }
}

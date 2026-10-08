package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** Reads a live world for the shelter rules and the bone placement. Use on the world's thread. */
final class WorldBlocks implements StormShelter.Blocks {

    private static final String FULL_HITBOX = "Full";

    private final World world;
    // Consecutive lookups are nearly always in one chunk, so remember the last one.
    private long cachedIndex = Long.MIN_VALUE;
    private WorldChunk cachedChunk;

    WorldBlocks(World world) {
        this.world = world;
    }

    WorldChunk chunkAt(int x, int z) {
        long index = ChunkUtil.indexChunkFromBlock(x, z);
        if (index != cachedIndex) {
            cachedIndex = index;
            cachedChunk = world.getChunkIfLoaded(index);
        }
        return cachedChunk;
    }

    private static boolean inWorld(int y) {
        return y >= ChunkUtil.MIN_Y && y < ChunkUtil.HEIGHT;
    }

    @Override
    public int topBlockY(int x, int z) {
        WorldChunk chunk = chunkAt(x, z);
        return chunk != null ? chunk.getHeight(x & ChunkUtil.SIZE_MASK, z & ChunkUtil.SIZE_MASK) : ChunkUtil.MIN_Y;
    }

    @Override
    public int skyLight(int x, int y, int z) {
        WorldChunk chunk = chunkAt(x, z);
        if (chunk == null || !inWorld(y)) {
            return -1;
        }
        BlockSection section = chunk.getBlockChunk().getSectionAtBlockY(y);
        // Same source the NPC light sensor uses. It is missing until the chunk has been lit.
        return section != null && section.hasGlobalLight() ? section.getGlobalLight().getSkyLight(x, y, z) : -1;
    }

    /**
     * A block counts as solid when its material is Solid and it has the default "Full" hitbox.
     * Plants are material Empty; doors, fences, slabs, stairs and torches have other hitboxes;
     * fluids are not blocks at all.
     */
    @Override
    public boolean solid(int x, int y, int z) {
        WorldChunk chunk = chunkAt(x, z);
        if (chunk == null || !inWorld(y)) {
            return false;
        }
        BlockType type = chunk.getBlockType(x, y, z);
        return type != null && type.getMaterial() == BlockMaterial.Solid && FULL_HITBOX.equals(type.getHitboxType());
    }

    /** True if the block holds nothing: no block and no fluid. */
    boolean empty(int x, int y, int z) {
        WorldChunk chunk = chunkAt(x, z);
        return chunk != null && inWorld(y) && chunk.getBlock(x, y, z) == 0 && fluidId(chunk, x, y, z) == 0;
    }

    /** What WorldChunk.getFluidId does; that method is marked for removal. */
    private static int fluidId(WorldChunk chunk, int x, int y, int z) {
        Ref<ChunkStore> column = chunk.getReference();
        if (column == null || !column.isValid()) {
            return 0;
        }
        Store<ChunkStore> store = column.getStore();
        ChunkColumn sections = store.getComponent(column, ChunkColumn.getComponentType());
        Ref<ChunkStore> section = sections != null ? sections.getSection(ChunkUtil.chunkCoordinate(y)) : null;
        FluidSection fluids = section != null && section.isValid()
                ? store.getComponent(section, FluidSection.getComponentType()) : null;
        return fluids != null ? fluids.getFluidId(x, y, z) : 0;
    }
}

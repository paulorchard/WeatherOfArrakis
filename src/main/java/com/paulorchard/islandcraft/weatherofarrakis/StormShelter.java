package com.paulorchard.islandcraft.weatherofarrakis;

/**
 * Decides whether a spot is sheltered from the storm. It only sees the world through
 * {@link Blocks}, so the rules can be tested without a server.
 */
public final class StormShelter {

    public enum Verdict {
        /** Nothing above the head at all. */
        EXPOSED_OPEN_SKY(false),
        /** So little sky light reaches the head that this is a cave, tunnel or closed room. */
        SHELTERED_DEEP_COVER(true),
        /** Something is overhead, but no solid roof close enough. */
        EXPOSED_NO_ROOF(false),
        /** A roof, but the wind reaches the feet or the head. */
        EXPOSED_NO_WINDBREAK(false),
        /** A roof overhead and solid cover upwind at both feet and head height. */
        SHELTERED_LEE_SIDE(true);

        public final boolean sheltered;

        Verdict(boolean sheltered) {
            this.sheltered = sheltered;
        }
    }

    /** The three things the rules need to know about the world. */
    public interface Blocks {
        /** Y of the highest block in the column, of any kind. */
        int topBlockY(int x, int z);

        /** Stored sky light, 0 to 15, or -1 if it is not known. */
        int skyLight(int x, int y, int z);

        /** True for a full block with collision. */
        boolean solid(int x, int y, int z);
    }

    private StormShelter() {
    }

    /**
     * Checks the player whose feet are in block (x, feetY, z). The first rule with an answer wins.
     * Worst case: 1 height lookup, 1 light lookup, roofBlocks + 2 * windbreakBlocks block lookups.
     */
    public static Verdict check(Blocks blocks, int x, int feetY, int z, WindDirection windFrom,
                                int deepCoverSkyLight, int roofBlocks, int windbreakBlocks) {
        int headY = feetY + 1;

        if (blocks.topBlockY(x, z) <= headY) {
            return Verdict.EXPOSED_OPEN_SKY;
        }

        int light = blocks.skyLight(x, headY, z);
        if (light >= 0 && light <= deepCoverSkyLight) {
            return Verdict.SHELTERED_DEEP_COVER;
        }

        if (!anySolid(blocks, x, headY, z, 0, 1, 0, roofBlocks)) {
            return Verdict.EXPOSED_NO_ROOF;
        }
        boolean feetCovered = anySolid(blocks, x, feetY, z, windFrom.upwindX, 0, windFrom.upwindZ, windbreakBlocks);
        boolean headCovered = feetCovered
                && anySolid(blocks, x, headY, z, windFrom.upwindX, 0, windFrom.upwindZ, windbreakBlocks);
        return headCovered ? Verdict.SHELTERED_LEE_SIDE : Verdict.EXPOSED_NO_WINDBREAK;
    }

    /** True if any of the next {@code count} blocks from (x, y, z), stepping by (dx, dy, dz), is solid. */
    private static boolean anySolid(Blocks blocks, int x, int y, int z, int dx, int dy, int dz, int count) {
        for (int step = 1; step <= count; step++) {
            if (blocks.solid(x + dx * step, y + dy * step, z + dz * step)) {
                return true;
            }
        }
        return false;
    }
}

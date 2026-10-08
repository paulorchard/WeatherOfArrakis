package com.paulorchard.islandcraft.weatherofarrakis;

import com.paulorchard.islandcraft.weatherofarrakis.StormShelter.Verdict;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shelter cases from the design, built from blocks. The wind is from the west (-X),
 * so the lee side of anything is its east (+X) side. The ground is the layer y = 0; a
 * player standing on it has feet at y = 1 and head at y = 2.
 */
class StormShelterTest {

    /** A tiny world: a set of solid blocks, full sky light unless a spot is told otherwise. */
    private static final class FakeBlocks implements StormShelter.Blocks {
        private final Set<String> solid = new HashSet<>();
        private final Map<String, Integer> light = new HashMap<>();

        FakeBlocks() {
            fill(-20, 20, 0, 0, -5, 5);
        }

        FakeBlocks fill(int x1, int x2, int y1, int y2, int z1, int z2) {
            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    for (int z = z1; z <= z2; z++) {
                        solid.add(x + "," + y + "," + z);
                    }
                }
            }
            return this;
        }

        FakeBlocks clear(int x, int y, int z) {
            solid.remove(x + "," + y + "," + z);
            return this;
        }

        FakeBlocks light(int x, int y, int z, int value) {
            light.put(x + "," + y + "," + z, value);
            return this;
        }

        @Override
        public int topBlockY(int x, int z) {
            for (int y = 40; y >= 0; y--) {
                if (solid(x, y, z)) {
                    return y;
                }
            }
            return 0;
        }

        @Override
        public int skyLight(int x, int y, int z) {
            return light.getOrDefault(x + "," + y + "," + z, 15);
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return solid.contains(x + "," + y + "," + z);
        }
    }

    private static Verdict standingAt(FakeBlocks blocks, int x) {
        return StormShelter.check(blocks, x, 1, 0, WindDirection.WEST, 2, 3, 2);
    }

    /** A rock face at x = -1, six blocks high, with a ledge sticking out two blocks on each side at y = 4. */
    private static FakeBlocks rockWithOverhang() {
        return new FakeBlocks().fill(-1, -1, 1, 6, -5, 5).fill(-3, 1, 4, 4, -5, 5);
    }

    /** A room five blocks deep (x = 0..4) with walls at x = -1 and x = 5 and a ceiling at y = 4. */
    private static FakeBlocks room() {
        return new FakeBlocks()
                .fill(-1, -1, 1, 4, -5, 5).fill(5, 5, 1, 4, -5, 5).fill(-1, 5, 4, 4, -5, 5)
                .fill(-1, 5, 1, 4, -5, -5).fill(-1, 5, 1, 4, 5, 5);
    }

    @Test
    void openSandIsExposed() {
        assertEquals(Verdict.EXPOSED_OPEN_SKY, standingAt(new FakeBlocks(), 0));
    }

    @Test
    void deepInACaveIsSheltered() {
        FakeBlocks cave = new FakeBlocks().fill(-20, 20, 4, 30, -5, 5).light(0, 2, 0, 0);
        assertEquals(Verdict.SHELTERED_DEEP_COVER, standingAt(cave, 0));
    }

    @Test
    void closedRoomOnTheSurfaceIsSheltered() {
        // No opening, so no sky light gets in. The spot is too far from the west wall for the lee rule.
        assertEquals(Verdict.SHELTERED_DEEP_COVER, standingAt(room().light(3, 2, 0, 0), 3));
    }

    @Test
    void leeSideOfARockUnderAnOverhangIsSheltered() {
        assertEquals(Verdict.SHELTERED_LEE_SIDE, standingAt(rockWithOverhang(), 0));
        assertEquals(Verdict.SHELTERED_LEE_SIDE, standingAt(rockWithOverhang(), 1));
    }

    @Test
    void windwardSideOfTheSameRockIsExposed() {
        assertEquals(Verdict.EXPOSED_NO_WINDBREAK, standingAt(rockWithOverhang(), -2));
    }

    @Test
    void underASingleFloatingBlockIsExposed() {
        assertEquals(Verdict.EXPOSED_NO_WINDBREAK, standingAt(new FakeBlocks().fill(0, 0, 4, 4, 0, 0), 0));
    }

    @Test
    void doorwayOpenToTheWestIsExposed() {
        FakeBlocks westDoor = room().clear(-1, 1, 0).clear(-1, 2, 0);
        assertEquals(Verdict.EXPOSED_NO_WINDBREAK, standingAt(westDoor, -1));
    }

    @Test
    void roofFourBlocksUpWithAWallBehindIsExposed() {
        FakeBlocks highRoof = new FakeBlocks().fill(-1, -1, 1, 6, -5, 5).fill(-1, 1, 6, 6, -5, 5);
        assertEquals(Verdict.EXPOSED_NO_ROOF, standingAt(highRoof, 0));
    }

    @Test
    void headCoveredButFeetOpenToTheWindIsExposed() {
        // A wall with a gap at ground level: the wind reaches the feet.
        FakeBlocks gap = rockWithOverhang().clear(-1, 1, 0);
        assertEquals(Verdict.EXPOSED_NO_WINDBREAK, standingAt(gap, 0));
    }

    @Test
    void unknownSkyLightFallsThroughToTheLeeRule() {
        assertEquals(Verdict.SHELTERED_LEE_SIDE, standingAt(rockWithOverhang().light(0, 2, 0, -1), 0));
    }

    /**
     * Differs from the design, which wants this spot sheltered. The doorway is lit by the sky
     * and the nearest block upwind is the far wall, six blocks away, so the rules as written
     * call it exposed. See EXPERIMENT_LOG.md for the suggested rule change.
     */
    @Test
    void doorwayOpenToTheEastIsExposedUnderTheRulesAsWritten() {
        FakeBlocks eastDoor = room().clear(5, 1, 0).clear(5, 2, 0);
        assertEquals(Verdict.EXPOSED_NO_WINDBREAK, standingAt(eastDoor, 5));
        // Two blocks in from the west wall the same room does shelter.
        assertEquals(Verdict.SHELTERED_LEE_SIDE, standingAt(eastDoor, 1));
    }
}

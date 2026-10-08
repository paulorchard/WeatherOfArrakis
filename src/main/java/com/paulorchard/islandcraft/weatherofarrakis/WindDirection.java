package com.paulorchard.islandcraft.weatherofarrakis;

/**
 * Where the storm blows from. In Hytale east is +X and north is -Z
 * (Vector3dUtil.EAST, NORTH), and the sun rises at +X and sets at -X.
 */
public enum WindDirection {
    WEST(-1, 0),
    EAST(1, 0),
    NORTH(0, -1),
    SOUTH(0, 1);

    /** One block step towards the wind's source. */
    public final int upwindX;
    public final int upwindZ;

    WindDirection(int upwindX, int upwindZ) {
        this.upwindX = upwindX;
        this.upwindZ = upwindZ;
    }
}

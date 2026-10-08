package com.paulorchard.islandcraft.weatherofarrakis;

/** The stages of the Coriolis storm cycle, in the order they run. */
public enum CoriolisPhase {
    /** No storm. The next one is scheduled in game time. */
    CALM,
    /** The storm has been announced and the sky is turning. Timed in real seconds. */
    APPROACH,
    /** The storm is overhead. Timed in real seconds. */
    STORM,
    /** The forced weather has been released and the forecast is blending back in. */
    CLEARING
}

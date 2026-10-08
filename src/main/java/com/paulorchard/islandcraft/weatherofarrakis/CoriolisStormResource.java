package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.time.Instant;

/**
 * One world's storm state. Saved with the world, so a restart resumes in the same phase.
 */
public class CoriolisStormResource implements Resource<EntityStore> {

    public static final String ID = "Arrakis_Coriolis_Storm";

    public static final BuilderCodec<CoriolisStormResource> CODEC =
            BuilderCodec.builder(CoriolisStormResource.class, CoriolisStormResource::new)
                    .append(new KeyedCodec<>("Phase", new EnumCodec<>(CoriolisPhase.class), false),
                            (state, value) -> state.phase = value,
                            state -> state.phase)
                    .add()
                    .append(new KeyedCodec<>("NextStormGameTime", Codec.INSTANT, false),
                            (state, value) -> state.nextStormGameTime = value,
                            state -> state.nextStormGameTime)
                    .add()
                    .append(new KeyedCodec<>("PhaseElapsedSeconds", Codec.DOUBLE, false),
                            (state, value) -> state.phaseElapsedSeconds = value,
                            state -> state.phaseElapsedSeconds)
                    .add()
                    .append(new KeyedCodec<>("ApproachSeconds", Codec.DOUBLE, false),
                            (state, value) -> state.approachSeconds = value,
                            state -> state.approachSeconds)
                    .add()
                    .append(new KeyedCodec<>("StormSeconds", Codec.DOUBLE, false),
                            (state, value) -> state.stormSeconds = value,
                            state -> state.stormSeconds)
                    .add()
                    .append(new KeyedCodec<>("ClearingSeconds", Codec.DOUBLE, false),
                            (state, value) -> state.clearingSeconds = value,
                            state -> state.clearingSeconds)
                    .add()
                    .build();

    CoriolisPhase phase = CoriolisPhase.CALM;
    /** Game time at which APPROACH begins. Null until the first tick in a qualifying world. */
    Instant nextStormGameTime;
    /** Real seconds spent in the current phase. Unused in CALM. */
    double phaseElapsedSeconds;
    /** Lengths chosen for the current run, so a shortened test run survives a restart. */
    double approachSeconds;
    double stormSeconds;
    double clearingSeconds;

    /** Index of the next chat warning to send during APPROACH. -1 means "work it out from the clock". Not saved. */
    transient int nextWarningIndex = -1;

    public CoriolisPhase getPhase() {
        return phase != null ? phase : CoriolisPhase.CALM;
    }

    public Instant getNextStormGameTime() {
        return nextStormGameTime;
    }

    /** Length of the current phase in real seconds, or 0 in CALM. */
    public double getPhaseLengthSeconds() {
        return switch (getPhase()) {
            case APPROACH -> approachSeconds;
            case STORM -> stormSeconds;
            case CLEARING -> clearingSeconds;
            case CALM -> 0.0;
        };
    }

    /** Real seconds left in the current phase, or 0 in CALM. */
    public double getPhaseRemainingSeconds() {
        return Math.max(0.0, getPhaseLengthSeconds() - phaseElapsedSeconds);
    }

    @Override
    public Resource<EntityStore> clone() {
        CoriolisStormResource copy = new CoriolisStormResource();
        copy.phase = phase;
        copy.nextStormGameTime = nextStormGameTime;
        copy.phaseElapsedSeconds = phaseElapsedSeconds;
        copy.approachSeconds = approachSeconds;
        copy.stormSeconds = stormSeconds;
        copy.clearingSeconds = clearingSeconds;
        copy.nextWarningIndex = nextWarningIndex;
        return copy;
    }
}

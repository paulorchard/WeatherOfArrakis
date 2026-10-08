package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;

/** Settings read from Weather_of_Arrakis.json in the plugin's data folder. */
public class WeatherOfArrakisConfig {

    public static final String FILE_NAME = "Weather_of_Arrakis";

    public static final BuilderCodec<WeatherOfArrakisConfig> CODEC =
            BuilderCodec.builder(WeatherOfArrakisConfig.class, WeatherOfArrakisConfig::new)
                    .documentation("Settings for the Coriolis storm.")
                    .append(new KeyedCodec<>("StormIntervalMinDays", Codec.DOUBLE, false),
                            (config, value) -> config.stormIntervalMinDays = value,
                            config -> config.stormIntervalMinDays)
                    .documentation("Shortest gap between storms, in in-game days.")
                    .add()
                    .append(new KeyedCodec<>("StormIntervalMaxDays", Codec.DOUBLE, false),
                            (config, value) -> config.stormIntervalMaxDays = value,
                            config -> config.stormIntervalMaxDays)
                    .documentation("Longest gap between storms, in in-game days.")
                    .add()
                    .append(new KeyedCodec<>("StormDurationMinSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.stormDurationMinSeconds = value,
                            config -> config.stormDurationMinSeconds)
                    .documentation("Shortest storm, in real seconds.")
                    .add()
                    .append(new KeyedCodec<>("StormDurationMaxSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.stormDurationMaxSeconds = value,
                            config -> config.stormDurationMaxSeconds)
                    .documentation("Longest storm, in real seconds.")
                    .add()
                    .append(new KeyedCodec<>("ApproachSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.approachSeconds = value,
                            config -> config.approachSeconds)
                    .documentation("How long before arrival the storm is announced, in real seconds.")
                    .add()
                    .append(new KeyedCodec<>("WarningSeconds", Codec.DOUBLE_ARRAY, false),
                            (config, value) -> config.warningSeconds = value,
                            config -> config.warningSeconds)
                    .documentation("Seconds before arrival at which a chat warning is sent, longest first. "
                            + "The first entry uses the announcement text and the last one the take-cover text.")
                    .add()
                    .append(new KeyedCodec<>("ClearingSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.clearingSeconds = value,
                            config -> config.clearingSeconds)
                    .documentation("How long the normal forecast takes to blend back in, in real seconds.")
                    .add()
                    .append(new KeyedCodec<>("GeneratorTypes", Codec.STRING_ARRAY, false),
                            (config, value) -> config.generatorTypes = value,
                            config -> config.generatorTypes)
                    .documentation("World generator types the storm runs in. Add \"Flat\" to test in a flat world.")
                    .add()
                    .append(new KeyedCodec<>("StageBlendFraction", Codec.DOUBLE, false),
                            (config, value) -> config.stageBlendFraction = value,
                            config -> config.stageBlendFraction)
                    .documentation("How much of each approach stage is spent blending into it, from 0 to 1. "
                            + "0 leaves the game's own 10 second blend.")
                    .add()
                    .append(new KeyedCodec<>("WindFrom", new EnumCodec<>(WindDirection.class), false),
                            (config, value) -> config.windFrom = value,
                            config -> config.windFrom)
                    .documentation("Compass direction the storm blows from: West, East, North or South.")
                    .add()
                    .append(new KeyedCodec<>("DeepCoverSkyLight", Codec.INTEGER, false),
                            (config, value) -> config.deepCoverSkyLight = value,
                            config -> config.deepCoverSkyLight)
                    .documentation("A player whose head is in sky light at or below this (0 to 15) is sheltered with no further checks.")
                    .add()
                    .append(new KeyedCodec<>("RoofBlocks", Codec.INTEGER, false),
                            (config, value) -> config.roofBlocks = value,
                            config -> config.roofBlocks)
                    .documentation("Lee-side shelter needs a solid block within this many blocks above the head.")
                    .add()
                    .append(new KeyedCodec<>("WindbreakBlocks", Codec.INTEGER, false),
                            (config, value) -> config.windbreakBlocks = value,
                            config -> config.windbreakBlocks)
                    .documentation("Lee-side shelter needs a solid block within this many blocks upwind, at both feet and head height.")
                    .add()
                    .append(new KeyedCodec<>("ExposureGraceChecks", Codec.INTEGER, false),
                            (config, value) -> config.exposureGraceChecks = value,
                            config -> config.exposureGraceChecks)
                    .documentation("Exposed checks in a row, one per second, that pass before the storm starts to hurt.")
                    .add()
                    .append(new KeyedCodec<>("DurabilityLossPerSecond", Codec.DOUBLE, false),
                            (config, value) -> config.durabilityLossPerSecond = value,
                            config -> config.durabilityLossPerSecond)
                    .documentation("Durability each worn or held item loses per second in the open.")
                    .add()
                    .append(new KeyedCodec<>("HealthLossArmoured", Codec.DOUBLE, false),
                            (config, value) -> config.healthLossArmoured = value,
                            config -> config.healthLossArmoured)
                    .documentation("Health lost per second in the open while wearing armour that has durability.")
                    .add()
                    .append(new KeyedCodec<>("HealthLossStripped", Codec.DOUBLE, false),
                            (config, value) -> config.healthLossStripped = value,
                            config -> config.healthLossStripped)
                    .documentation("Health lost per second in the open with no such armour.")
                    .add()
                    .append(new KeyedCodec<>("BoneBlock", Codec.STRING, false),
                            (config, value) -> config.boneBlock = value,
                            config -> config.boneBlock)
                    .documentation("Block left where a player dies during the storm. Empty for none.")
                    .add()
                    .build();

    private double stormIntervalMinDays = 3.0;
    private double stormIntervalMaxDays = 6.0;
    private double stormDurationMinSeconds = 40.0;
    private double stormDurationMaxSeconds = 120.0;
    private double approachSeconds = 300.0;
    private double[] warningSeconds = {300.0, 240.0, 180.0, 120.0, 60.0, 30.0};
    private double clearingSeconds = 30.0;
    private String[] generatorTypes = {"Dunes_of_Arrakis"};
    private double stageBlendFraction = 0.9;
    private WindDirection windFrom = WindDirection.WEST;
    private int deepCoverSkyLight = 2;
    private int roofBlocks = 3;
    private int windbreakBlocks = 2;
    private int exposureGraceChecks = 2;
    private double durabilityLossPerSecond = 4.0;
    private double healthLossArmoured = 2.0;
    private double healthLossStripped = 10.0;
    private String boneBlock = "Deco_Bone_Pile";

    public double getStormIntervalMinDays() {
        return Math.max(0.0, Math.min(stormIntervalMinDays, stormIntervalMaxDays));
    }

    public double getStormIntervalMaxDays() {
        return Math.max(0.0, Math.max(stormIntervalMinDays, stormIntervalMaxDays));
    }

    public double getStormDurationMinSeconds() {
        return Math.max(1.0, Math.min(stormDurationMinSeconds, stormDurationMaxSeconds));
    }

    public double getStormDurationMaxSeconds() {
        return Math.max(1.0, Math.max(stormDurationMinSeconds, stormDurationMaxSeconds));
    }

    public double getApproachSeconds() {
        return Math.max(1.0, approachSeconds);
    }

    public double[] getWarningSeconds() {
        return warningSeconds != null ? warningSeconds : new double[0];
    }

    public double getClearingSeconds() {
        return Math.max(0.0, clearingSeconds);
    }

    public String[] getGeneratorTypes() {
        return generatorTypes != null ? generatorTypes : new String[0];
    }

    public double getStageBlendFraction() {
        return Math.max(0.0, Math.min(1.0, stageBlendFraction));
    }

    public WindDirection getWindFrom() {
        return windFrom != null ? windFrom : WindDirection.WEST;
    }

    public int getDeepCoverSkyLight() {
        return deepCoverSkyLight;
    }

    public int getRoofBlocks() {
        return Math.max(0, roofBlocks);
    }

    public int getWindbreakBlocks() {
        return Math.max(0, windbreakBlocks);
    }

    public int getExposureGraceChecks() {
        return Math.max(0, exposureGraceChecks);
    }

    public double getDurabilityLossPerSecond() {
        return Math.max(0.0, durabilityLossPerSecond);
    }

    public double getHealthLossArmoured() {
        return Math.max(0.0, healthLossArmoured);
    }

    public double getHealthLossStripped() {
        return Math.max(0.0, healthLossStripped);
    }

    public String getBoneBlock() {
        return boneBlock != null ? boneBlock : "";
    }
}

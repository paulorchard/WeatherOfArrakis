package com.paulorchard.islandcraft.weatherofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

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
}

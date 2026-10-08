# IslandCraft - Weather of Arrakis

## Naming convention

- Family name `IslandCraft - <mod name>`; manifest group `IslandCraft`.
- Identifier form `<Mod_Name_With_Underscores>` (here `Weather_of_Arrakis`) for the mod's asset folders and its config file name.
- Code form `<ModNameNoSpaces>` (here `WeatherOfArrakis`); package `com.paulorchard.islandcraft.<modnamelowercase>`; jar and repository `IslandCraft-<ModNameNoSpaces>`.
- In-world content is prefixed `Arrakis_` (weathers `Arrakis_Coriolis_...`, ambience `Arrakis_AmbFX_Coriolis_...`, the saved world resource `Arrakis_Coriolis_Storm`); no abbreviated asset prefixes. Asset IDs are global.
- Language keys: chat text under `weatherOfArrakis.`, command text under `commands.coriolis.`, both in `Server/Languages/en-US/server.lang` (the code adds the `server.` prefix).
- Projects live under `C:\Apps\IslandCraft\`.

Server version: 0.6.8

## Iteration loop

- `gradlew deployMod` builds the jar and replaces this mod's jar in `%APPDATA%\Hytale\UserData\Mods`. The game locks the jar while a world is open: exit to the main menu first.
- Weather and ambience JSON are generated. Edit the tables in `tools/weathers/build.js`, run `node tools/weathers/build.js`, then deploy. The generated files are committed.
- Headless test, no client needed: `tools/headless/run.sh "wait 20" "coriolis status" "coriolis start 10 6" "wait 12" "coriolis status" "weather get"` boots the real server in `build/headless` with this mod and Dunes of Arrakis, types the commands into its console and prints the replies. A second run is a restart of the same world. Commands run on different threads, so put a `wait 1` between two that must run in order. It cannot show sky, light or sound.
- Asset check: add `--validate-assets --shutdown-after-validate` to the server command line. It always exits "FAILED" because of vanilla instance files; read the log for this mod's names instead.
- In game: `/coriolis start 60 30` gives a one-minute approach with the stages and warnings squeezed to fit. Compare with `/weather set Zone2_Sand_Storm`, then `/weather reset`.

## Engine facts confirmed from the 0.6.8 server jar

Weather:

- `WeatherSystem.TickingSystem` sends each player their weather once a second, and at once when the forced weather changes. `JOIN_TRANSITION_SECONDS` is 0.5 (first send after joining a world or teleporting) and `WEATHERCHANGE_TRANSITION_SECONDS` is 10. A forced weather always blends in over those fixed 10 seconds.
- `WeatherTracker.sendWeatherIndex` sends nothing if the player is already on that weather index. So a plugin can send each player the weather itself, with any transition time, and then force the same weather: the game's own send becomes a no-op and the plugin's transition time stands. This is how the long stage blends are sent.
- `WeatherTracker.updateWeather` checks a per-player override first, then the world's forced weather, and only then the player's environment. A forced weather therefore reaches every player in the world whatever environment they are in, underground included.
- `/weather set` writes both the `WeatherResource` and the world config (`WorldConfig.setForcedWeather`, saved). This mod only touches the resource, so a crash cannot leave a storm weather stuck in a save, and at CLEARING it puts back whatever the world config holds.
- While a weather is forced, the hourly forecast is not rolled. On release the game rolls it for the current hour on the next tick.
- Weather JSON keys (from the `Weather` codec): `SunlightDampingMultipliers` (note the plural; a list of `Hour`/`Value`), `ColorFilters` (`Hour`/`Color`), `SkyBottomColors` and `ScreenEffectColors` (`Hour`/`Color` with alpha), `FogOptions` (`IgnoreFogLimits`, `EffectiveViewDistanceMultiplier`, `FogHeightCameraFixed`, `FogHeightCameraOffset`), `Particle` (`SystemId`, `Color`, `Scale`, `OvergroundOnly`, `PositionOffsetMultiplier`).
- No vanilla weather uses `SunlightDampingMultipliers` or `ScreenEffectColors` with alpha below full. `ColorFilters` is used once (`Portals_Void_Event_Intense`, `#fd07c8ff`). The codec has no description for any of the three, so their exact effect is only known by trying them.
- `FogDistance` is `[FogNear, FogFar]` and must be increasing. The client has a minimum and maximum for FogFar unless `FogOptions.IgnoreFogLimits` is true. Shortest vanilla value: `[-95, 45]` (`Dungeon_Cursed_Crypt`). Highest vanilla `FogDensities`: 1.2.
- `Zone2_Sunny` has no `SunlightColors`. `Zone2_Desert_Haze` keeps a bright sunlight colour at midnight, so the client darkens the night by itself and the colour is a multiplier on top.

Sound:

- `AmbientBed.Volume` is in decibels (`AudioUtil.decibelsToLinearGain`). The codec only accepts -100 to +10 dB.
- Several AmbienceFX assets can match at once and their beds layer. The vanilla sandstorm is three: `Sandstorm_Stereo_LOOP` at -9, a desert loop at +10 and `Z2_Wind_Base_Low` at +1, all on `WeatherTagPattern` `Sandstorm` with `Shelter: [Open, Partial]`.
- A weather tag pattern matches the values inside the weather's `Tags` map: `{"Arrakis": ["Coriolis_Storm"]}` is matched by `Tag: "Coriolis_Storm"`.
- `Shelter` values: `Open`, `Partial`, `Sheltered`, `Enclosed`. `TransitionSpeed`: `Default`, `Fast`, `Instant`.
- There is no vanilla interior sandstorm ambience. The Interior folder only has rain, which plays a separate interior recording on `Shelter: [Sheltered]`.

Time, plugins, commands:

- `WorldTimeResource.getGameTime()` is the saved game clock; a day is 86400 game seconds. The real length of a day is `World.getDaytimeDurationSeconds() + getNighttimeDurationSeconds()` (1728 + 1152). `WorldTimeResource.DAYTIME_SECONDS` and `NIGHTTIME_SECONDS` are game seconds, not real ones.
- `registerResource(Class, id, codec)` on the entity store registry gives a per-world resource saved as `universe/worlds/<world>/resources/<id>.json`.
- `withConfig(name, codec)` must be called before `setup()`. The server loads `<data folder>/<name>.json` but never creates it; the plugin saves the defaults itself on first run.
- The plugin data folder is `mods/<Group>_<Name>` beside the universe. In single player that is inside the save: `Saves/<world>/mods/IslandCraft_IslandCraft - Weather of Arrakis/Weather_of_Arrakis.json`. The config is per save.
- The server treats everything in a mods folder as a pack, so a data folder logs `Skipping pack at ...: missing or invalid manifest.json`. The game's own `Hytale_Shop` and `Hytale_HytaleGenerator` folders log the same line in every save.
- A plugin command gets a generated permission node. With no permission group set, only operators have it.
- Optional positional arguments are done with `addUsageVariant`: one unnamed command per argument count. `withOptionalArg` makes a `--name value` argument instead.
- `IWorldGenProvider.CODEC.getIdFor(provider.getClass())` gives a world's generator type as written in its config.
- `PlayerReadyEvent` is keyed by world name (`registerGlobal`) and fires when a client has finished loading into a world.

## Prompt 14: storm life cycle, sky, sound, warnings

### What was built

- `CoriolisStormSystem`: one state machine per world, CALM, APPROACH, STORM, CLEARING. The schedule is a game-time instant, so sleeping brings the storm closer; the three active phases count real seconds. If the clock is set back so far that the storm is more than the longest gap away, it is rescheduled.
- `CoriolisStormResource`: phase, scheduled time, seconds elapsed in the phase, and the lengths chosen for this run. Saved with the world.
- `CoriolisStorm`: the query class for prompts 15 and 16. `getPhase(store)`, `getSecondsRemaining(store)`, `addPhaseListener(listener)`. Listeners run on the world thread on every phase change.
- `/coriolis status | start [approachSeconds] [stormSeconds] | stop`.
- Seven weathers and nine ambience assets, generated by `tools/weathers/build.js`.
- Config `Weather_of_Arrakis.json`: `StormIntervalMinDays` 3, `StormIntervalMaxDays` 6, `StormDurationMinSeconds` 40, `StormDurationMaxSeconds` 120, `ApproachSeconds` 300, `WarningSeconds` [300, 240, 180, 120, 60, 30], `ClearingSeconds` 30, `GeneratorTypes` ["Dunes_of_Arrakis"], `StageBlendFraction` 0.9.

### Findings asked for

1. Transition time. Forcing a weather uses a fixed 10 s. A longer one can be sent per player and is not overwritten (see the facts above); the mod sends each approach stage with a blend of 90% of the stage's length (54 s for the one-minute stages, 27 s for the last two). Whether the client blends smoothly over that long is not yet seen. If it pops or stalls, set `StageBlendFraction` to 0 for the game's 10 s steps.
   One 300 s blend straight to stage 6 was not used: the sound follows the tags of the weather the client is on, so a single visual blend would leave nothing to step the volume with.
2. Underground. A forced weather is sent to every player in every environment, caves included. `OvergroundOnly` keeps the sand particles out of caves, but fog, sky colour, the screen overlay and the sound conditions are not limited by the server. What a cave looks like during the storm has to be seen in game. If it is wrong, `WeatherTracker.setOverrideWeatherIndex` can give sheltered players a different weather; that belongs with the shelter work in prompt 15.

### Weather stages

Colours are blends from a clear day (`Zone2_Sunny`) to the storm palette; the horizon passes through bright orange on the way so it does not go grey.

| Weather | Sky | Horizon | Sunlight | FogDistance | FogDensity | Extras | Sand loop |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `Arrakis_Coriolis_Approach_1` | 5% | 35% | 100% | -96, 1024 | 0.05 | | -29 dB |
| `Arrakis_Coriolis_Approach_2` | 25% | 55% | 100% | -96, 1024 | 0.15 | | -23 dB |
| `Arrakis_Coriolis_Approach_3` | 50% | 75% | 95% | -96, 1024 | 0.30 | | -19.5 dB |
| `Arrakis_Coriolis_Approach_4` | 75% | 90% | 90% | -96, 1024 | 0.45 | | -17 dB |
| `Arrakis_Coriolis_Approach_5` | 100% | 100% | 75% | -96, 512 | 0.60 | sand particles, no moon or stars | -15 dB |
| `Arrakis_Coriolis_Approach_6` | 100% | 100% | 55% | -96, 256 | 0.75 | particles, overlay at 40% | -13.5 dB |
| `Arrakis_Coriolis_Storm` | 100% | 100% | 20% | -48, 40 | 1.2 | particles at Scale 1.5, full overlay, `IgnoreFogLimits`, colour filter `#e6b088` | 0 dB, wind loop +3 dB, indoors -12 dB |

Each weather has its own tag (`Arrakis: [Coriolis_Approach_1]` ... `[Coriolis_Storm]`) and none has `Sandstorm`, so the vanilla beds do not play on top.

### The 80% light loss

- Key used: `SunlightColors`, one value for all hours. The storm is `#332b22`, which is 20% of a warm white (`#ffd9a8`). The client dims sunlight at night by itself, so one value for every hour means night stays darker than day.
- `SunlightDampingMultipliers` was not used. Nothing in the server or the vanilla assets says whether 0.2 means "20% of the light" or "20% of the damping", and the wrong guess would make the storm brighter. To try it, add `SunlightDampingMultipliers: allDay(0.2)` to the storm in `build.js` and set its `light` back to 1.
- `ColorFilters` `#e6b088` is added as a mild warm cast. It is the least certain key; remove `filter` from the storm row if the picture looks wrong.
- How dark it really is: not measured. It needs eyes on the game at noon and at midnight.

### Sound

- Volume is in decibels, and the server refuses a bed above +10 dB. That is the ceiling.
- Storm: the sandstorm loop at 0 dB (9 dB over the vanilla bed, about 2.8 times the amplitude) with `Z2_Wind_Base_Mid` layered at +3 dB. Indoors (`Sheltered`, `Enclosed`): the sandstorm loop alone at -12 dB. There is no interior sandstorm recording, so "muffled" is quieter, not filtered.
- Where it stops getting louder or starts to distort: not tested, it needs ears. Raise `STORM_LOOP_DB` in `build.js` in steps of 3 up to 10.

### Checks

Done on the headless server:

- Clean build; `deployMod` leaves one jar for this mod in the Mods folder.
- Pack and plugin load. The only log line naming this mod is the data-folder `Skipping pack` warning described above. No missing-asset, unknown-key or unused-key warnings for its weathers, ambience or language file.
- New Dunes of Arrakis world: first storm scheduled 4.86 days away; later rolls 4.66, 5.32, 5.84.
- `/coriolis start 10 6`: APPROACH, then STORM, then CLEARING with the next storm scheduled, then CALM. `/weather get` shows `Arrakis_Coriolis_Approach_2` and `Arrakis_Coriolis_Storm` while forced and "not locked" afterwards.
- Stopped the server 6 s into a 60 s approach: on restart it was in APPROACH with 40 s left and stage 2 forced again. Stopped it 5.5 s into an 8 s storm: on restart it finished the storm and cleared.
- `/coriolis stop` in CLEARING says nothing is active; in APPROACH it clears and reschedules. Not tried in CALM or STORM.
- `GeneratorTypes` changed to `["Flat"]` and restarted: the Dunes world reports that storms do not run there, `/coriolis start` is refused, and its state goes back to CALM.
- Default config file is written on first run and re-read on restart.

Not done, needs the game client:

- The mod list entry.
- All chat messages, their timing and colour. No player was connected, so none were sent. The text comes from language keys that resolved correctly for the command replies.
- Every look and sound check: sky steps or blend, stage volumes, the storm against `/weather set Zone2_Sand_Storm`, the 30 s return to normal weather, a vanilla sandstorm being replaced.
- Fog distance of 15 to 25 blocks. `[-48, 40]` with `IgnoreFogLimits` is a first guess from the vanilla range.
- The join message for a player arriving mid-storm.
- Screenshots of stages 1, 3, 6 and the storm at noon and midnight.

### Differences from the design

- Warning text shows the real time left, so a shortened test approach says "Coriolis storm in 48 seconds." and not "in 4 minutes".
- The join message has its own two texts ("A Coriolis storm arrives in 3 min 37 s. Find shelter." and "A Coriolis storm is raging. Stay in shelter.").
- `/coriolis stop` during APPROACH also sends "The storm has passed.", as the all-clear for players who were warned.
- CLEARING sends each player the forecast weather with a 30 s blend. If the hour has changed since the storm began, the game rolls a new forecast a tick later and blends to that in its usual 10 s.
- Stages 5 and 6 use the vanilla sand particles at normal size; only the storm scales them up. `Scale` is described as "the scale of the particle system", which may mean size and not amount.
- One extra config value, `StageBlendFraction`.

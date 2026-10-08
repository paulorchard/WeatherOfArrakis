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

## Prompt 15: exposure, gear loss, death and bones

### Engine facts added

- Directions: `Vector3dUtil.EAST` is +X, `WEST` is -X, `NORTH` is -Z. Running `WorldTimeResource.getSunDirection()` through a day gives light travelling towards -X at 06:00 and towards +X at 18:00, so the sun rises at +X and sets at -X. West is -X. Not checked against the in-game compass.
- Vanilla drowning (`DamageSystems.CanBreathe`) builds `new Damage(Damage.NULL_SOURCE, DamageCause.DROWNING, 10f)` and passes it to `DamageSystems.executeDamage` with the system's command buffer. Storm damage takes the same route.
- Damage cause keys: `DurabilityLoss`, `StaminaLoss`, `BypassResistances`, `Inherits`, `DamageTextColor`, `AnimationId`, `DeathAnimationId`. `Environment.json` is `DurabilityLoss: true, StaminaLoss: false, BypassResistances: true`; drowning, suffocation and fall inherit it.
- `BypassResistances` is read in one place, `DamageSystems.ArmorDamageReduction`, which skips the armour resistance step when it is true. `DurabilityLoss` is read by `DamageArmor`, which is what wears armour when vanilla damage lands.
- The death message comes from the damage source: `Damage.Source.getDeathMessage`. The default is `server.general.killedBy` with `server.general.damageCauses.<cause id in lower case>`, giving "You were killed by ...!".
- Dying adds a `DeathComponent`; `DeathSystems.OnDeathSystem` is the base class vanilla uses to react to it.
- `Inventory.getArmor()`, `getHotbar()`, `getUtility()`, `getTools()` and the active-slot getters are deprecated for removal. The replacement is one component per section: `InventoryComponent.Armor`, `.Hotbar`, `.Utility`, `.Tool`, each with `getInventory()`; the last three also have `getActiveSlot()`, and `Tool.isUsingToolsItem()` says whether the hand holds the tool-belt item.
- Sky light: `BlockSection.getGlobalLight().getSkyLight(x, y, z)`, 0 to 15, the source the NPC light sensor uses. It is the stored exposure to the sky and does not change with the time of day. A section has none until it has been lit (`hasGlobalLight()`).
- `WorldChunk.getHeight(x, z)` is the height map; `getBlockType(x, y, z)` takes world coordinates.
- Block properties: `Material` is `Empty` or `Solid`; `HitboxType` defaults to `Full`. `WorldChunk.setBlock(x, y, z, id, type, rotation, filler, settings)` places a block; rotation is a `RotationTuple` index.
- Bone blocks: `Deco_Bone_Full` is a plain cube named "Bone", not a skeleton. `Deco_Bone_Pile` ("Pile of Bones") and `Deco_Bone_Skulls` ("Pile of Skulls") are ground decorations with a client-side random yaw (`RandomRotation: YawStep1`) and no rotation variants. `Deco_Bone_Ribs`, `Ribs_Long` and `Spine` are tube and wall pieces that need support.
- `SFX_Item_Break` is the sound vanilla plays when an item breaks.

### What was built

- `CoriolisExposureSystem`: once a second during STORM, for each living non-Creative player: shelter check, messages on a change of state, then gear and health loss after the grace.
- `StormShelter`: the rules, with no server types in them. `WorldBlocks` reads the live world for it.
- `CoriolisStormDamage`: the damage source, with the death message. Recognise storm damage by cause id `Arrakis_Coriolis_Storm` or by `damage.getSource() instanceof CoriolisStormDamage`.
- `CoriolisBonesSystem`: places the bone block when a player dies during STORM.
- New config values: `WindFrom` West, `DeepCoverSkyLight` 2, `RoofBlocks` 3, `WindbreakBlocks` 2, `ExposureGraceChecks` 2, `DurabilityLossPerSecond` 4, `HealthLossArmoured` 2, `HealthLossStripped` 10, `BoneBlock` Deco_Bone_Pile. The config file is now rewritten on every start so an older file gains the new keys.

### Decisions

- Solid means `Material: Solid` and `HitboxType: Full`. Plants are material Empty. Doors, fences, slabs, stairs, torches and the bone piles have other hitboxes and do not count. Fluids are stored apart from blocks and never count. Leaves are full solid blocks and do count.
- The damage cause is `DurabilityLoss: false, StaminaLoss: false, BypassResistances: true`. It does not inherit `Environment`, because that would switch vanilla armour wear back on and the storm already takes durability itself.
- The exposure message is sent on the first exposed check, before the grace has run out, so the player has the two seconds to react. The first state of each storm is announced to everyone, sheltered or not.
- Health loss is decided after the gear step, so the second the last armour piece is torn away already costs 10.
- An item already at 0 durability (vanilla "broken") is taken on the first damaging second.
- The bone block defaults to `Deco_Bone_Pile`, not `Deco_Bone_Full`. Neither has been looked at in game.
- Bones: death position, then down up to 8, then the eight neighbours at the death height. A spot must hold no block and no fluid and have a solid block under it.
- Block lookups per exposed player per second, worst case: 1 height map, 1 sky light, 3 block types for the roof, 4 for the windbreak. Nine in all; a sheltered cave costs two and open sand one.

### Checks

Done:

- 11 unit tests of the shelter rules pass (`gradlew test`): open sand, cave, closed room, lee side under an overhang, windward side, floating block, west doorway, roof 4 up, feet exposed through a gap, unknown sky light, east doorway.
- Headless server: the damage cause loads (16 causes, 15 vanilla), the config gains the new keys, a storm runs from start to clearing with both new systems registered and nothing logged.
- Clean build, deployed, one jar.

Not done, needs a player in the game: every check in the prompt's list. Nothing that touches a player has run: the messages, durability loss, items vanishing, the break sound, health loss at 2 and 10, Creative mode, the death message, bones and their placement cases, the shelter cases on real terrain and their screenshots, tick time.

### Expected to differ from the design

- Doorway open to the east: the design wants it sheltered, the rules as written make it exposed unless the room is at most 2 blocks deep. Standing in the doorway the sky light is high, and the nearest solid block upwind is the far wall. The unit test `doorwayOpenToTheEastIsExposedUnderTheRulesAsWritten` pins this.
  Suggested rule: let the windbreak search carry on under a roof. Walk upwind from the player; keep going while each block passed has a solid roof within `RoofBlocks`; the player is sheltered if a solid block is reached at both feet and head height before the roof runs out, up to about 16 blocks. A west doorway still fails, because its opening is reached before any wall.
- Closed room with a window or an open door: the same thing. Anyone more than 2 blocks from the west wall is exposed unless the room is dark enough for deep cover. The rule above fixes this too.
- Whether a shield raised against the storm reduces the damage is not known. `WieldingDamageReduction` does not look at `BypassResistances`; it may only act on hits with a direction.
- Hurt sound and flash once a second: not heard. If it is too much, deal the health loss every 2 seconds at double the amount; the kill time stays the same.

## Prompt 16: lightning, camera shake, storm front

### Engine facts added

- `ParticleUtil.spawnParticleEffect(id, position, accessor)` sends to players within `DEFAULT_PARTICLE_DISTANCE`, 75 blocks. The overloads that take a list of player refs send to exactly those players at any distance.
- A particle system has `CullDistance`, `BoundingRadius` and `IsImportant`; vanilla values of `CullDistance` run from 1 to 1000. A particle has `CameraFarFadeStartDistance` and `CameraFarFadeEndDistance`. Spawner `RenderMode` values seen: `BlendLinear`, `BlendAdd`. `LightInfluence` 0 means unlit (the spell lightning); the vanilla sand storm uses 0.6.
- The weather's own `Particle` system follows the camera (`PositionOffsetMultiplier` moves it ahead of the view), so it cannot sit in one compass direction.
- Spell lightning: `Lightning_Trail` draws `Particles/Textures/Shapes/Lightning.png` (three 64 x 128 frames) with `BlendAdd`, `BillboardY`, scale 1 to 1.6 by 20, over an emit column 4.5 high.
- `SoundUtil.playSoundEvent3d(index, category, x, y, z, float, float, accessor)`: the floats are volume modifier and pitch modifier (the packet fields `volumeModifier`, `pitchModifier`).
- Sound event keys: `Volume` (dB), `Pitch`, `StartAttenuationDistance`, `MaxDistance`, `Layers` (each with `Files`, `Volume`, `RandomSettings`, `StartDelay`), `PreventSoundInterruption`. The vanilla thunder event has a 1.5 s start delay and inherits `SFX_Attn_Quiet` (heard to 15 blocks); the loudest preset, `SFX_Attn_VeryLoud`, reaches 70.
- Camera shake: a `CameraShake` asset has `FirstPerson` and `ThirdPerson`, each with `Duration`, `EaseIn`, `EaseOut` (`Time`, `Type`) and noise lists for `Offset` X/Y/Z and `Rotation` Pitch/Yaw/Roll (`Frequency`, `Amplitude`, `Type`: Sin, Cos, Perlin_Linear, Perlin_Hermite, Perlin_Quintic, Random). Vanilla only uses Sin and Cos, on roll.
- A shake is played by sending `CameraShakeEffect(cameraShakeIndex, intensity, AccumulationMode)` with `PacketHandler.writeNoCache`. `CameraEffect` assets are a wrapper that supplies the intensity; vanilla intensities are 0.05 with amplitudes of 0.5 to 1.5. The camera classes belong to the `Hytale:Camera` plugin, now a dependency.
- Cloud layers have `Texture`, `Colors` and `Speeds` and nothing else: no direction, offset or coverage key.
- With no player connected no chunks are loaded, so the headless server cannot run anything that needs a block position.

### What was built

- `CoriolisLightningSystem`: during STORM, every 2 to 6 s, one strike per cluster of players (within 96 blocks of each other), 12 to 60 blocks from a random member, on the top block of that column. Each strike: bolt particles to players within 300 blocks, thunder at the spot, a camera jolt within 20 blocks that weakens with distance, a sky flash within 96 blocks, and 25 damage to exposed, living, non-Creative players within 3 blocks.
- Bolt: new particle system `Arrakis_Coriolis_Lightning`. The spell bolt is about 4.5 blocks of emit height; this one stacks 14 copies of the same texture up a 40-block column at 3 to 5 by 24 scale, unlit and additive, for under half a second, with a soft ball of light and the vanilla sparks and poof at the foot.
- Thunder: new sound event `Arrakis_SFX_Coriolis_Thunder`, the three vanilla recordings with no delay, +4 dB on the event and +6 dB on the layer, full volume to 40 blocks and audible to 160.
- Flash: a one-moment weather swap. `Arrakis_Coriolis_Flash` is the storm weather with white sunlight and brightened fog and sky. A nearby player gets it as a per-player override with a 0.05 s blend, and the storm back 0.15 s later with a 0.4 s blend. It carries the storm's tag so the sound beds should not react. Switch: `LightningFlash`.
- Shakes: `Arrakis_Coriolis_Arrival` (about 2 s, fading), `Arrakis_Coriolis_Lightning` (under half a second), `Arrakis_Coriolis_Tremble` (just over a second, re-sent on every exposed check). Strengths are config values, sent directly in the packet.
- `/coriolis strike` hits the caller's own column; `/coriolis strike x z` hits a chosen one and works from the console. Both work in any phase.
- Storm front, option A: `CoriolisFrontSystem` and the particle system `Arrakis_Coriolis_Front`, off by default (`StormFront`).
- Config additions: `LightningMinSeconds` 2, `LightningMaxSeconds` 6, `LightningMinDistance` 12, `LightningMaxDistance` 60, `LightningDamageRadius` 3, `LightningDamage` 25, `LightningFlash` true, `LightningShakeRadius` 20, `LightningShakeIntensity` 0.03, `ArrivalShakeIntensity` 0.08, `ExposedTremble` true, `ExposedTrembleIntensity` 0.008, `StormFront` false, `StormFrontDistance` 120.

### Storm front: where the three options stand

None has been seen, so none has been judged.

- A, particle wall: built. Every 2.5 s each player is sent, to them alone, a curtain of 90 soft sand-coloured puffs (scale 28 to 40, about 5 s life) spread 70 high and 140 to each side, placed upwind at `StormFrontDistance` x (time left / approach length). Lit by the world so it darkens at night. One packet per player per 2.5 s on the server. Open questions, all for the client: whether particles draw at 120 blocks (`CullDistance` is set to 600; vanilla goes to 1000), whether one particle can be that large, whether the approach fog hides it (stages 1 to 4 keep FogFar at 1024 with density up to 0.45), and the frame-rate cost of 180 or so large overlapping transparent quads.
- B, cloud layer: not built. A cloud layer has no direction or offset key, so the server cannot choose which way a band crosses the sky; it would depend on how the client maps and scrolls the texture, and that is only found by looking. It also needs a painted texture, which cannot be judged blind.
- C, horizon tint: ruled out without building. `SkySunsetColors` is keyed by hour only and is drawn around the sun. It would sit in the west only when the sun does, which fails "at any hour".

To try A: set `StormFront` to true in the save's config, reload the world, `/coriolis start`. If it does not convince, delete `CoriolisFrontSystem`, the `front` blocks in `tools/weathers/effects.js`, the two config values and the `frontTimer` field.

### Checks

Done on the headless server:

- All new assets load with no warning naming them: 3 particle spawners, 2 particle systems, 1 sound event, 1 weather, 3 camera shakes.
- The config gains the 14 new keys. A storm runs start to clearing with the lightning and front systems registered; nothing logged.
- `/coriolis strike x z` runs from the console and reports an unloaded column correctly. With no player there is no loaded column, so no strike has actually gone off.
- 11 shelter unit tests still pass. Clean build, deployed, one jar.

Not done, needs the game client: every item in the prompt's check list. No bolt, flash, thunder or shake has been seen or heard.

First things to look at, because they are guesses:

- Bolt size and height. `BOLT_HEIGHT` and the scales are in `effects.js`.
- The flash. If swapping weathers restarts the sand particles, the screen overlay or the sound, it will flicker every few seconds: set `LightningFlash` to false.
- Shake strengths. 0.08, 0.03 and 0.008 are scaled from vanilla's 0.05; the units are not documented.
- The tremble. On by default as asked. It is roll and pitch only, at 7 to 13 Hz and a tenth of the arrival strength. If it is unpleasant, set `ExposedTremble` to false and say so, and the default will change.
- Thunder loudness against the storm bed.

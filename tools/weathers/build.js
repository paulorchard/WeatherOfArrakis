// Writes the Coriolis weather and ambience assets from the tables below.
// usage: node tools/weathers/build.js
// Edit the tables, run it, then gradlew deployMod. The JSON files it writes are committed.
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '../../src/main/resources/Server');
const weatherDir = path.join(root, 'Weathers/Weather_of_Arrakis');
const ambienceDir = path.join(root, 'Audio/AmbienceFX/Ambience/Weather_of_Arrakis');

// ---- colour helpers -------------------------------------------------------

const parse = hex => [1, 3, 5, 7].map(i => (i < hex.length ? parseInt(hex.slice(i, i + 2), 16) : 255));
const format = (c, withAlpha) =>
  '#' + c.slice(0, withAlpha ? 4 : 3).map(v => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join('');
const mix = (a, b, t) => parse(a).map((v, i) => v + (parse(b)[i] - v) * t);
const lerp = (a, b, t) => a + (b - a) * t;

// Each palette entry is [night, twilight, day]. Hours follow the vanilla sandstorm's keyframes.
const HOURS = [[3, 0], [5, 1], [7, 2], [17, 2], [19, 1], [21, 0]];
const overDay = (clear, storm, t, withAlpha) =>
  HOURS.map(([hour, period]) => ({ Hour: hour, Color: format(mix(clear[period], storm[period], t), withAlpha) }));
// Same, but passing through a third palette at the half-way point. A straight blend from
// blue to brown goes through grey; the horizon should go through bright orange.
const overDayVia = (clear, via, storm, t, withAlpha) =>
  t < 0.5 ? overDay(clear, via, t * 2, withAlpha) : overDay(via, storm, t * 2 - 1, withAlpha);
const allDay = value => Array.from({ length: 24 }, (_, hour) => ({ Hour: hour, Value: +value.toFixed(3) }));

// ---- palettes -------------------------------------------------------------

// "Clear" is Zone2_Sunny, the commonest Arrakis weather, sampled at the hours above.
const clear = {
  skyTop: ['#01333dff', '#017a9fff', '#0081b2ff'],
  skyBottom: ['#836b7eff', '#b1f3feff', '#b1f3feff'],
  sunset: ['#db7753ff', '#db775399', '#ffffff00'],
  fog: ['#836b7e', '#b1f3fe', '#b1f3fe'],
  cloud: ['#47351f00', '#eaaa6c00', '#d19c8000'],
};

// The storm: deep orange-red sky over brown.
const storm = {
  skyTop: ['#1a0d07ff', '#5e2c14ff', '#7d4520ff'],
  skyBottom: ['#23110aff', '#8a3c17ff', '#a8521fff'],
  sunset: ['#3a1608ff', '#b0431aff', '#c2501cff'],
  fog: ['#1d1009', '#6e3a1c', '#8f5228'],
  cloud: ['#24140cff', '#6b3719ff', '#8a4a22ff'],
};

const MOONS = ['Full', 'Gibbous', 'Half', 'Crescent', 'New'].map((name, day) => ({ Day: day, Texture: `Sky/MoonCycle/Moon_${name}.png` }));

// The horizon half way through the approach: a bright orange band under a sky that is still blue.
const glow = {
  skyBottom: ['#5a2c18ff', '#e08a4cff', '#e89858ff'],
  sunset: ['#8a3a18ff', '#e8742cff', '#f08a3cd0'],
};

// ---- stages ---------------------------------------------------------------
// sky/horizon/fogColor: 0 = clear day, 1 = storm colours.
// light: sunlight as a share of a clear day.   fog: [FogNear, FogFar].   density: FogDensities.
// cloud: opacity of the storm cloud layers.    sand: Sand_Storm particles (true, or a Scale).
// overlay: opacity of the sand screen overlay. volume: sandstorm loop in dB (vanilla storm is -9).

const stages = [
  { id: 'Arrakis_Coriolis_Approach_1', tag: 'Coriolis_Approach_1', sky: 0.05, horizon: 0.35, fogColor: 0.05, light: 1.00, fog: [-96, 1024], density: 0.05, cloud: 0.15, sky_night: true, volume: -29 },
  { id: 'Arrakis_Coriolis_Approach_2', tag: 'Coriolis_Approach_2', sky: 0.25, horizon: 0.55, fogColor: 0.25, light: 1.00, fog: [-96, 1024], density: 0.15, cloud: 0.30, sky_night: true, volume: -23 },
  { id: 'Arrakis_Coriolis_Approach_3', tag: 'Coriolis_Approach_3', sky: 0.50, horizon: 0.75, fogColor: 0.50, light: 0.95, fog: [-96, 1024], density: 0.30, cloud: 0.50, sky_night: true, volume: -19.5 },
  { id: 'Arrakis_Coriolis_Approach_4', tag: 'Coriolis_Approach_4', sky: 0.75, horizon: 0.90, fogColor: 0.75, light: 0.90, fog: [-96, 1024], density: 0.45, cloud: 0.70, sky_night: true, volume: -17 },
  { id: 'Arrakis_Coriolis_Approach_5', tag: 'Coriolis_Approach_5', sky: 1.00, horizon: 1.00, fogColor: 1.00, light: 0.75, fog: [-96, 512], density: 0.60, cloud: 0.85, sand: true, volume: -15 },
  { id: 'Arrakis_Coriolis_Approach_6', tag: 'Coriolis_Approach_6', sky: 1.00, horizon: 1.00, fogColor: 1.00, light: 0.55, fog: [-96, 256], density: 0.75, cloud: 1.00, sand: true, overlay: 0.4, volume: -13.5 },
  { id: 'Arrakis_Coriolis_Storm', tag: 'Coriolis_Storm', sky: 1.00, horizon: 1.00, fogColor: 1.00, light: 0.20, fog: [-48, 40], density: 1.2, ignoreFogLimits: true, cloud: 1.00, sand: 1.5, overlay: 1, filter: '#e6b088' },
];

// Storm sound, outdoors and in. The server rejects an ambient bed above +10 dB.
const STORM_LOOP_DB = 0;
const STORM_WIND_DB = 3;
const STORM_INTERIOR_DB = -12;

// ---- weather --------------------------------------------------------------

function weather(s) {
  // Sunlight colour: white drifting to a warm tint, scaled by the light share. The client
  // dims it further at night by itself, so one value for every hour keeps night below day.
  const sunlight = format(mix('#ffffff', '#ffd9a8', s.sky).map(v => v * s.light));
  const cloudLayer = (texture, speed, brighten) => ({
    Texture: texture,
    Colors: HOURS.map(([hour, period]) => {
      const colour = mix(clear.cloud[period], storm.cloud[period], 1).map((v, i) => (i < 3 ? v * brighten : v * s.cloud));
      return { Hour: hour, Color: format(colour, true) };
    }),
    Speeds: [{ Hour: 0, Value: +lerp(1, speed, s.sky).toFixed(1) }],
  });

  const w = {
    Tags: { Arrakis: [s.tag] },
    // The moon and stars stay until the sky has gone fully over.
    ...(s.sky_night ? { Stars: 'Sky/Stars.png', Moons: MOONS } : { Moons: [] }),
    Clouds: [cloudLayer('Sky/Clouds/Twirled.png', 40, 1), cloudLayer('Sky/Clouds/Scars.png', 25, 1.18)],
    SkyTopColors: overDay(clear.skyTop, storm.skyTop, s.sky, true),
    SkyBottomColors: overDayVia(clear.skyBottom, glow.skyBottom, storm.skyBottom, s.horizon, true),
    SkySunsetColors: overDayVia(clear.sunset, glow.sunset, storm.sunset, s.horizon, true),
    FogColors: overDay(clear.fog, storm.fog, s.fogColor, false),
    FogDistance: s.fog,
    FogHeightFalloffs: allDay(4),
    FogDensities: allDay(s.density),
    SunlightColors: [{ Hour: 0, Color: sunlight }],
    SunScales: allDay(lerp(1, 0.3, s.sky)),
    MoonScales: allDay(0.8),
    SunColors: [{ Hour: 7, Color: format(mix('#ffffff', '#e07a3a', s.sky)) }],
    SunGlowColors: [{ Hour: 7, Color: format(mix('#ffffffff', '#e07a3a80', s.sky), true) }],
  };
  if (s.ignoreFogLimits) w.FogOptions = { IgnoreFogLimits: true };
  if (s.sand) w.Particle = { SystemId: 'Sand_Storm', OvergroundOnly: true, ...(s.sand === true ? {} : { Scale: s.sand }) };
  if (s.overlay) {
    w.ScreenEffect = 'ScreenEffects/Sand.png';
    if (s.overlay < 1) w.ScreenEffectColors = [{ Hour: 0, Color: format([255, 255, 255, 255 * s.overlay], true) }];
  }
  if (s.filter) w.ColorFilters = [{ Hour: 0, Color: s.filter }];
  return w;
}

// ---- ambience -------------------------------------------------------------

const bed = (tag, shelter, track, volume, extra = {}) => ({
  AudioCategory: 'AudioCat_Ambient',
  Conditions: { Shelter: shelter, WeatherTagPattern: { Op: 'Equals', Tag: tag } },
  AmbientBed: { Track: track, Volume: volume, ...extra },
});

const SAND_LOOP = 'Sounds/Environments/Global/Weather/Sandstorm_Stereo_LOOP.ogg';
const WIND_LOOP = 'Sounds/Environments/Zone2/Global/Wind/Z2_Wind_Base_Mid_Stereo_LOOP.ogg';
const OUTSIDE = ['Open', 'Partial'];
const INSIDE = ['Sheltered', 'Enclosed'];

const ambience = {};
for (const s of stages) {
  if (s.volume !== undefined) ambience[`Arrakis_AmbFX_${s.tag}`] = bed(s.tag, OUTSIDE, SAND_LOOP, s.volume);
}
ambience.Arrakis_AmbFX_Coriolis_Storm = bed('Coriolis_Storm', OUTSIDE, SAND_LOOP, STORM_LOOP_DB);
ambience.Arrakis_AmbFX_Coriolis_Storm_Wind = bed('Coriolis_Storm', OUTSIDE, WIND_LOOP, STORM_WIND_DB);
ambience.Arrakis_AmbFX_Coriolis_Storm_Interior = bed('Coriolis_Storm', INSIDE, SAND_LOOP, STORM_INTERIOR_DB, { TransitionSpeed: 'Fast' });

// ---- write ----------------------------------------------------------------

function writeAll(dir, assets) {
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  for (const [id, asset] of Object.entries(assets)) {
    fs.writeFileSync(path.join(dir, id + '.json'), JSON.stringify(asset, null, 2) + '\n');
  }
  console.log(`${Object.keys(assets).length} files in ${path.relative(process.cwd(), dir)}`);
}

writeAll(weatherDir, Object.fromEntries(stages.map(s => [s.id, weather(s)])));
writeAll(ambienceDir, ambience);

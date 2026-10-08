// Lightning, thunder, camera shake and storm front assets. Run through build.js, not directly.
const fs = require('fs');
const path = require('path');

const range = (min, max) => ({ Min: min, Max: max });
const xyz = (x, y, z) => ({ X: x, Y: y, Z: z });
const still = { Speed: range(0, 0), Yaw: range(0, 0), Pitch: range(0, 0) };
const steady = { Scale: { X: range(1, 1), Y: range(1, 1) }, Rotation: xyz(range(0, 0), range(0, 0), range(0, 0)) };

// ---- lightning flash ------------------------------------------------------
// The storm weather with the light turned up. Players near a strike are switched to it
// for a moment. It keeps the storm's tag, so the sound beds do not notice the swap.

function flashWeather(storm, { format, mix }) {
  const brighten = (list, withAlpha) => list.map(k => ({ Hour: k.Hour, Color: format(mix(k.Color, '#ffe2b8ff', 0.6), withAlpha) }));
  const w = { ...storm };
  w.SunlightColors = [{ Hour: 0, Color: '#fff0d8' }];
  w.FogColors = brighten(w.FogColors, false);
  w.SkyTopColors = brighten(w.SkyTopColors, true);
  w.SkyBottomColors = brighten(w.SkyBottomColors, true);
  delete w.ColorFilters;
  return w;
}

// ---- particles ------------------------------------------------------------

// The bolt: the spell lightning texture, stretched tall and thin and stacked up a column.
const BOLT_HEIGHT = 40;
const bolt = {
  RenderMode: 'BlendAdd',
  EmitOffset: xyz(range(0, 0.6), range(0, BOLT_HEIGHT), range(0, 0.6)),
  ParticleRotationInfluence: 'BillboardY',
  LinearFiltering: true,
  LightInfluence: 0,
  MaxConcurrentParticles: 500,
  ParticleLifeSpan: range(0.35, 0.5),
  SpawnRate: range(400, 400),
  TotalParticles: range(14, 14),
  InitialVelocity: still,
  Particle: {
    Texture: 'Particles/Textures/Shapes/Lightning.png',
    FrameSize: { Width: 64, Height: 128 },
    UVOption: 'RandomFlipU',
    Animation: {
      0: { FrameIndex: range(0, 0), ...steady, Opacity: 0 },
      6: { Opacity: 1 },
      35: { FrameIndex: range(1, 1) },
      60: { FrameIndex: range(2, 2), Opacity: 0.9 },
      100: { Opacity: 0 },
    },
    InitialAnimationFrame: {
      Rotation: xyz(range(0, 0), range(90, 90), range(0, 0)),
      Scale: { X: range(3, 5), Y: range(24, 24) },
      Opacity: 1,
      FrameIndex: range(0, 0),
    },
  },
};

// A soft ball of light at the foot of the bolt.
const glow = {
  RenderMode: 'BlendAdd',
  EmitOffset: xyz(range(0, 0), range(0, 0), range(0, 0)),
  ParticleRotationInfluence: 'Billboard',
  LinearFiltering: true,
  LightInfluence: 0,
  MaxConcurrentParticles: 10,
  ParticleLifeSpan: range(0.3, 0.3),
  SpawnRate: range(100, 100),
  TotalParticles: range(2, 2),
  InitialVelocity: still,
  Particle: {
    Texture: 'Particles/Textures/Basic/Ball3.png',
    FrameSize: { Width: 32, Height: 32 },
    ScaleRatioConstraint: 'OneToOne',
    Animation: { 0: { ...steady, Opacity: 0 }, 10: { Opacity: 0.8 }, 100: { Opacity: 0 } },
    InitialAnimationFrame: { Scale: { X: range(40, 40), Y: range(40, 40) }, Opacity: 1, Color: '#ffe9c0' },
  },
};

const lightning = {
  Spawners: [
    { SpawnerId: 'Arrakis_Coriolis_Lightning_Bolt', PositionOffset: xyz(0, 0, 0) },
    { SpawnerId: 'Arrakis_Coriolis_Lightning_Glow', PositionOffset: xyz(0, 2, 0) },
    { SpawnerId: 'Lightning_Sparks', PositionOffset: xyz(0, 0.5, 0) },
    { SpawnerId: 'Lightning_Poof', PositionOffset: xyz(0, 0.5, 0) },
  ],
  CullDistance: 300,
  IsImportant: true,
};

// The storm front: a curtain of big soft sand-coloured puffs. Its long side is Z, which is
// across a west or east wind; the plugin turns the system a quarter for north and south.
const FRONT_HALF_WIDTH = 140;
const FRONT_HEIGHT = 70;
const frontWall = {
  RenderMode: 'BlendLinear',
  EmitOffset: xyz(range(0, 6), range(0, FRONT_HEIGHT), range(0, FRONT_HALF_WIDTH)),
  ParticleRotationInfluence: 'Billboard',
  LinearFiltering: true,
  // Lit by the world, so the wall is dark at night like everything else.
  LightInfluence: 1,
  MaxConcurrentParticles: 400,
  ParticleLifeSpan: range(4.5, 5),
  SpawnRate: range(2000, 2000),
  TotalParticles: range(90, 90),
  InitialVelocity: still,
  Particle: {
    Texture: 'Particles/Textures/Smoke/Smoke_Smooth2.png',
    FrameSize: { Width: 64, Height: 64 },
    Animation: { 0: { ...steady, Color: '#a8521f', Opacity: 0 }, 25: { Opacity: 0.85 }, 75: { Opacity: 0.85 }, 100: { Opacity: 0 } },
    InitialAnimationFrame: {
      Rotation: xyz(range(0, 0), range(0, 0), range(0, 360)),
      Scale: { X: range(28, 40), Y: range(28, 40) },
      Opacity: 1,
      Color: '#a8521f',
      FrameIndex: range(0, 3),
    },
  },
};
const front = { Spawners: [{ SpawnerId: 'Arrakis_Coriolis_Front_Wall', PositionOffset: xyz(0, 0, 0) }], CullDistance: 600, IsImportant: true };

// ---- thunder --------------------------------------------------------------
// The vanilla thunder recordings, without the 1.5 s delay and short reach of the vanilla event.

const thunder = {
  AudioCategory: 'AudioCat_SFX',
  Layers: [{
    Files: [1, 2, 3].map(n => `Sounds/Environments/Global/Weather/Emitters/Thunder_Stereo_0${n}.ogg`),
    RandomSettings: { MinVolume: -3, MinPitch: -2, MaxPitch: 2 },
    Volume: 6.0,
  }],
  PreventSoundInterruption: true,
  Volume: 4.0,
  MaxDistance: 160,
  StartAttenuationDistance: 40,
};

// ---- camera shakes --------------------------------------------------------
// Each axis is a list of [frequency, amplitude, wave]. The strength comes from the config.

const shake = (duration, easeIn, easeOut, pitch, yaw, roll) => {
  const noise = (list, scale) => list.map(([Frequency, Amplitude, Type]) => ({ Frequency, Amplitude: Amplitude * scale, Type }));
  const view = scale => ({
    Duration: duration,
    EaseIn: { Time: easeIn, Type: 'Linear' },
    EaseOut: { Time: easeOut, Type: 'QuadInOut' },
    Offset: { X: [], Y: [], Z: [] },
    Rotation: { Pitch: noise(pitch, scale), Yaw: noise(yaw, scale), Roll: noise(roll, scale) },
  });
  return { FirstPerson: view(1), ThirdPerson: view(0.6) };
};

const shakes = {
  // About two seconds, fading out.
  Arrakis_Coriolis_Arrival: shake(0.8, 0.1, 1.3, [[17, 0.6, 'Sin'], [29, 0.3, 'Cos']], [[23, 0.5, 'Cos']], [[20, 1.0, 'Sin'], [31, 0.5, 'Sin']]),
  // A short jolt.
  Arrakis_Coriolis_Lightning: shake(0.1, 0.01, 0.35, [[33, 0.6, 'Sin']], [], [[40, 1.0, 'Sin']]),
  // Slow and small. Re-sent every second while a player is exposed, so it runs a little longer than that.
  Arrakis_Coriolis_Tremble: shake(0.9, 0.2, 0.3, [[7, 0.4, 'Sin'], [11, 0.2, 'Cos']], [], [[9, 0.5, 'Sin'], [13, 0.25, 'Cos']]),
};

// ---- write ----------------------------------------------------------------

function writeDir(root, sub, assets, ext = '.json') {
  const dir = path.join(root, sub);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  for (const [id, asset] of Object.entries(assets)) {
    fs.writeFileSync(path.join(dir, id + ext), JSON.stringify(asset, null, 2) + '\n');
  }
  console.log(`${Object.keys(assets).length} files in ${path.relative(process.cwd(), dir)}`);
}

function writeEffects(root) {
  writeDir(root, 'Particles/Weather_of_Arrakis', { Arrakis_Coriolis_Lightning: lightning, Arrakis_Coriolis_Front: front }, '.particlesystem');
  writeDir(root, 'Particles/Weather_of_Arrakis/Spawners', {
    Arrakis_Coriolis_Lightning_Bolt: bolt, Arrakis_Coriolis_Lightning_Glow: glow, Arrakis_Coriolis_Front_Wall: frontWall,
  }, '.particlespawner');
  writeDir(root, 'Audio/SoundEvents/Weather_of_Arrakis', { Arrakis_SFX_Coriolis_Thunder: thunder });
  writeDir(root, 'Camera/CameraShake/Weather_of_Arrakis', shakes);
}

module.exports = { flashWeather, writeEffects };

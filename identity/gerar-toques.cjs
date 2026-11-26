// Execute com Node.js 18+ a partir de qualquer diretório. O timbre é sintetizado localmente.
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");

const destination = path.resolve(__dirname, "../app/src/main/res/raw");
const sampleRate = 22050;
const period = 2 * Math.PI;
const tracks = [
  {
    name: "nox", length: 12, peak: 0.76,
    notes: [[0.06, 293.66], [1.54, 440.00], [3.05, 493.88], [4.52, 369.99],
      [6.04, 293.66], [7.51, 440.00], [9.02, 554.37]],
    decay: 0.88, overtone: 0.24,
  },
  {
    name: "dawn", length: 10, peak: 0.77,
    notes: [[0.05, 392.00], [1.31, 493.88], [2.56, 587.33], [3.79, 659.25],
      [5.02, 587.33], [6.26, 493.88], [7.52, 783.99]],
    decay: 0.70, overtone: 0.14,
  },
];

function chime(age, hz, decay, overtone) {
  if (age < 0) return 0;
  const attack = 1 - Math.exp(-age / 0.055);
  const tone = Math.sin(period * hz * age)
    + overtone * Math.exp(-age / 0.48) * Math.sin(period * 2 * hz * age)
    + 0.08 * Math.exp(-age / 0.25) * Math.sin(period * 3 * hz * age);
  return attack * Math.exp(-age / decay) * tone;
}

function synth(track) {
  const samples = new Float64Array(sampleRate * track.length);
  let max = 0;
  for (let i = 0; i < samples.length; i++) {
    const t = i / sampleRate;
    let sound = 0;
    for (const [start, frequency] of track.notes) {
      const amplitude = 0.62 + 0.38 * start / track.length;
      sound += amplitude * chime(t - start, frequency, track.decay, track.overtone);
      sound += 0.14 * amplitude * chime(t - start - 0.17, frequency, track.decay, 0.08);
    }
    const endFade = Math.min(1, (samples.length - i - 1) / (sampleRate * 0.18));
    samples[i] = sound * endFade;
    max = Math.max(max, Math.abs(samples[i]));
  }
  const dataSize = samples.length * 2;
  const wav = Buffer.alloc(44 + dataSize);
  wav.write("RIFF", 0);
  wav.writeUInt32LE(36 + dataSize, 4);
  wav.write("WAVEfmt ", 8);
  wav.writeUInt32LE(16, 16);
  wav.writeUInt16LE(1, 20); // PCM
  wav.writeUInt16LE(1, 22); // mono
  wav.writeUInt32LE(sampleRate, 24);
  wav.writeUInt32LE(2 * sampleRate, 28);
  wav.writeUInt16LE(2, 32);
  wav.writeUInt16LE(16, 34);
  wav.write("data", 36);
  wav.writeUInt32LE(dataSize, 40);
  for (let i = 0; i < samples.length; i++) {
    wav.writeInt16LE(Math.round(32767 * track.peak * samples[i] / max), 44 + 2 * i);
  }
  return wav;
}

function verify(track, file) {
  const wav = fs.readFileSync(file);
  assert.equal(wav.toString("ascii", 0, 4), "RIFF");
  assert.equal(wav.toString("ascii", 8, 16), "WAVEfmt ");
  assert.equal(wav.readUInt32LE(4) + 8, wav.length);
  assert.equal(wav.readUInt16LE(20), 1);
  assert.equal(wav.readUInt16LE(22), 1);
  assert.equal(wav.readUInt32LE(24), sampleRate);
  assert.equal(wav.readUInt16LE(34), 16);
  assert.equal(wav.readUInt32LE(40), wav.length - 44);
  assert.equal(wav.length - 44, 2 * sampleRate * track.length);
  let peak = 0, squareSum = 0;
  for (let i = 44; i < wav.length; i += 2) {
    const value = wav.readInt16LE(i) / 32767;
    peak = Math.max(peak, Math.abs(value));
    squareSum += value * value;
  }
  assert(Math.abs(peak - track.peak) < 1e-4);
  assert.equal(wav.readInt16LE(44), 0);
  assert.equal(wav.readInt16LE(wav.length - 2), 0);
  const rms = Math.sqrt(squareSum / ((wav.length - 44) / 2));
  assert(rms > 0.1 && rms < peak);
  console.log(track.name + ": " + track.length + " s, PCM mono 22050 Hz / 16 bit, pico "
    + (20 * Math.log10(peak)).toFixed(2) + " dBFS, RMS " + rms.toFixed(3));
}

fs.mkdirSync(destination, { recursive: true });
for (const track of tracks) {
  const file = path.join(destination, track.name + ".wav");
  fs.writeFileSync(file, synth(track));
  verify(track, file);
}

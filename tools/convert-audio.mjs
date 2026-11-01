// Turns the iOS game's audio into files Android plays: Core Audio (.caf) and WAV linear PCM become OGG Vorbis, the rest is copied as is.
// Needs `npm install` in this folder once (the encoder is a tool-time dependency only).
// Usage: node tools/convert-audio.mjs <iOS Resources/Audio folder> <app/src/main/assets/audio folder>
import { copyFileSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { createOggEncoder } from 'wasm-media-encoders';
import { dirname, extname, join, relative } from 'node:path';

const [source, destination] = process.argv.slice(2);
if (!source || !destination) throw new Error('Usage: node convert-audio.mjs <source> <destination>');

const oggEncoder = await createOggEncoder();
const VORBIS_QUALITY = 4;

function cafToPcm(bytes) {
  if (bytes.toString('latin1', 0, 4) !== 'caff') throw new Error('not a Core Audio file');
  let format = null;
  let data = null;
  for (let offset = 8; offset + 12 <= bytes.length; ) {
    const type = bytes.toString('latin1', offset, offset + 4);
    const size = Number(bytes.readBigInt64BE(offset + 4));
    const body = offset + 12;
    if (type === 'desc') {
      format = {
        rate: bytes.readDoubleBE(body),
        id: bytes.toString('latin1', body + 8, body + 12),
        flags: bytes.readUInt32BE(body + 12),
        channels: bytes.readUInt32BE(body + 24),
        bits: bytes.readUInt32BE(body + 28),
      };
    } else if (type === 'data') {
      data = bytes.subarray(body + 4, size === -1 ? bytes.length : body + size); // 4 bytes of edit count come first
    }
    if (size < 0) break;
    offset = body + size;
  }
  if (!format || !data) throw new Error('missing description or data');
  const littleEndian = (format.flags & 2) !== 0;
  if (format.id !== 'lpcm' || format.bits !== 16 || !littleEndian) throw new Error(`unsupported format ${JSON.stringify(format)}`);
  return splitChannels(data, format);
}

function splitChannels(data, format) {
  const frames = Math.floor(data.length / (format.channels * 2));
  const channels = Array.from({ length: format.channels }, (_, c) => {
    const samples = new Float32Array(frames);
    for (let i = 0; i < frames; i++) samples[i] = data.readInt16LE((i * format.channels + c) * 2) / 32768;
    return samples;
  });
  return { rate: format.rate, channels };
}

function wavToPcm(bytes) {
  if (bytes.toString('latin1', 0, 4) !== 'RIFF' || bytes.toString('latin1', 8, 12) !== 'WAVE') throw new Error('not a WAV file');
  let format = null;
  let data = null;
  for (let offset = 12; offset + 8 <= bytes.length; ) {
    const type = bytes.toString('latin1', offset, offset + 4);
    const size = bytes.readUInt32LE(offset + 4);
    const body = offset + 8;
    if (type === 'fmt ') format = { id: bytes.readUInt16LE(body), channels: bytes.readUInt16LE(body + 2), rate: bytes.readUInt32LE(body + 4), bits: bytes.readUInt16LE(body + 14) };
    else if (type === 'data') data = bytes.subarray(body, body + size);
    offset = body + size + (size % 2);
  }
  if (!format || !data || format.id !== 1 || format.bits !== 16) throw new Error('unsupported WAV');
  return splitChannels(data, format);
}

function pcmToOgg({ rate, channels }) {
  oggEncoder.configure({ sampleRate: rate, channels: channels.length, vbrQuality: VORBIS_QUALITY });
  const parts = [Buffer.from(oggEncoder.encode(channels)), Buffer.from(oggEncoder.finalize())];
  return Buffer.concat(parts);
}

function walk(folder) {
  return readdirSync(folder).flatMap((name) => {
    const path = join(folder, name);
    return statSync(path).isDirectory() ? walk(path) : [path];
  });
}

let converted = 0;
let copied = 0;
for (const file of walk(source)) {
  const target = join(destination, relative(source, file));
  mkdirSync(dirname(target), { recursive: true });
  const kind = extname(file);
  if (kind === '.caf' || kind === '.wav') {
    const decode = kind === '.caf' ? cafToPcm : wavToPcm;
    writeFileSync(target.replace(/\.(caf|wav)$/, '.ogg'), pcmToOgg(decode(readFileSync(file))));
    converted += 1;
  } else {
    copyFileSync(file, target);
    copied += 1;
  }
}
console.log(`converted ${converted} recordings, copied ${copied} files`);

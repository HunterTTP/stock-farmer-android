// Turns the iOS game's audio into files Android plays: Core Audio (.caf) linear PCM becomes WAV, the rest is copied as is.
// Usage: node tools/convert-audio.mjs <iOS Resources/Audio folder> <app/src/main/assets/audio folder>
import { copyFileSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, extname, join, relative } from 'node:path';

const [source, destination] = process.argv.slice(2);
if (!source || !destination) throw new Error('Usage: node convert-audio.mjs <source> <destination>');

function cafToWav(bytes) {
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
  const blockAlign = format.channels * 2;
  const header = Buffer.alloc(44);
  header.write('RIFF', 0, 'latin1');
  header.writeUInt32LE(36 + data.length, 4);
  header.write('WAVEfmt ', 8, 'latin1');
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(format.channels, 22);
  header.writeUInt32LE(format.rate, 24);
  header.writeUInt32LE(format.rate * blockAlign, 28);
  header.writeUInt16LE(blockAlign, 32);
  header.writeUInt16LE(16, 34);
  header.write('data', 36, 'latin1');
  header.writeUInt32LE(data.length, 40);
  return Buffer.concat([header, data]);
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
  if (extname(file) === '.caf') {
    writeFileSync(target.replace(/\.caf$/, '.wav'), cafToWav(readFileSync(file)));
    converted += 1;
  } else {
    copyFileSync(file, target);
    copied += 1;
  }
}
console.log(`converted ${converted} recordings, copied ${copied} files`);

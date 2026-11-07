// Release helpers for the tag-started Play upload (see docs/RELEASE.md).
// Usage: node tools/release.mjs version-code <tag>
//        node tools/release.mjs notes <tag> <output file>
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { pathToFileURL } from 'node:url';

export const CHANGELOG_PATH = 'app/src/main/assets/information/Changelog.md';
export const PLAY_NOTES_LIMIT = 500;
const TAG_PATTERN = /^(\d{4})\.([1-9]\d?)\.([1-9]\d?)-ga-([1-9]\d?)$/;

/** 2026.11.6-ga-1 becomes 2026110601: always rising, and readable in Play Console. */
export function versionCode(tag) {
  const match = TAG_PATTERN.exec(tag);
  if (!match) throw new Error(`Not a release tag (YYYY.M.D-ga-N): ${tag}`);
  const [year, month, day, counter] = match.slice(1).map(Number);
  if (month > 12 || day > 31) throw new Error(`Not a real date: ${tag}`);
  return year * 1_000_000 + month * 10_000 + day * 100 + counter;
}

/** The release tag just before this one, or null for the first release. */
export function previousTag(tag, tags) {
  const current = versionCode(tag);
  return (
    tags
      .filter((t) => TAG_PATTERN.test(t) && versionCode(t) < current)
      .sort((a, b) => versionCode(b) - versionCode(a))[0] ?? null
  );
}

/** Changelog entries, newest day first; a wrapped entry's indented lines join onto it. */
export function changelogEntries(markdown) {
  const days = [];
  for (const line of markdown.split(/\r?\n/)) {
    if (line.startsWith('## ')) days.push([]);
    else if (line.startsWith('- ') && days.length) days.at(-1).push(line.slice(2).trim());
    else if (/^\s+\S/.test(line) && days.at(-1)?.length) {
      const day = days.at(-1);
      day[day.length - 1] += ` ${line.trim()}`;
    }
  }
  return days;
}

/** Entries added since the previous release, or the newest day's when nothing new is found. */
export function newEntries(current, previous) {
  const days = changelogEntries(current);
  if (previous != null) {
    const shipped = new Set(changelogEntries(previous).flat());
    const added = days.flat().filter((entry) => !shipped.has(entry));
    if (added.length) return added;
  }
  return days[0] ?? [];
}

/** Whole entries that fit Play's limit; a first entry longer than the limit is cut short. */
export function playNotes(entries, limit = PLAY_NOTES_LIMIT) {
  const lines = [];
  let length = 0;
  for (const entry of entries) {
    const line = `• ${entry}`;
    const added = (lines.length ? 1 : 0) + line.length;
    if (length + added > limit) {
      if (!lines.length) lines.push(`${line.slice(0, limit - 1)}…`);
      break;
    }
    lines.push(line);
    length += added;
  }
  return lines.join('\n');
}

function git(...args) {
  return execFileSync('git', args, { encoding: 'utf8' });
}

function main([command, tag, output]) {
  if (command === 'version-code' && tag) {
    console.log(versionCode(tag));
  } else if (command === 'notes' && tag && output) {
    const previous = previousTag(tag, git('tag', '--list').split('\n').map((t) => t.trim()));
    const before = previous ? git('show', `${previous}:${CHANGELOG_PATH}`) : null;
    mkdirSync(dirname(output), { recursive: true });
    writeFileSync(output, `${playNotes(newEntries(readFileSync(CHANGELOG_PATH, 'utf8'), before))}\n`);
  } else {
    throw new Error('Usage: node tools/release.mjs version-code <tag> | notes <tag> <output file>');
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main(process.argv.slice(2));

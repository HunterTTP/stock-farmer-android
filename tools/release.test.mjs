import assert from 'node:assert/strict';
import { test } from 'node:test';
import { changelogEntries, newEntries, playNotes, previousTag, versionCode } from './release.mjs';

test('version code packs the date and the day counter', () => {
  assert.equal(versionCode('2026.11.6-ga-1'), 2026110601);
  assert.equal(versionCode('2026.1.31-ga-12'), 2026013112);
});

test('version codes rise across days and counters', () => {
  assert.ok(versionCode('2026.11.6-ga-2') > versionCode('2026.11.6-ga-1'));
  assert.ok(versionCode('2026.12.1-ga-1') > versionCode('2026.11.30-ga-99'));
  assert.ok(versionCode('2099.12.31-ga-99') < 2_100_000_000);
});

test('version code rejects anything but a release tag', () => {
  for (const tag of ['v1.0', '2026.11.06-ga-1', '2026.11.6-ga-0', '2026.13.1-ga-1', '2026.11.6-ga-1x']) {
    assert.throws(() => versionCode(tag), tag);
  }
});

test('previous tag is the newest release before this one', () => {
  const tags = ['2026.11.6-ga-1', '2026.11.6-ga-2', '2026.11.10-ga-1', 'other', ''];
  assert.equal(previousTag('2026.11.10-ga-1', tags), '2026.11.6-ga-2');
  assert.equal(previousTag('2026.11.6-ga-2', tags), '2026.11.6-ga-1');
  assert.equal(previousTag('2026.11.6-ga-1', tags), null);
});

const changelog = [
  '## 2026-11-03',
  '- New thing.',
  '- Wrapped entry that',
  '  continues here.',
  '',
  '## 2026-11-02',
  '- Old thing.',
].join('\n');

test('entries are grouped by day and wrapped lines are joined', () => {
  assert.deepEqual(changelogEntries(changelog), [
    ['New thing.', 'Wrapped entry that continues here.'],
    ['Old thing.'],
  ]);
});

test('new entries are those not in the previous release', () => {
  const previous = '## 2026-11-02\n- Old thing.\n';
  assert.deepEqual(newEntries(changelog, previous), ['New thing.', 'Wrapped entry that continues here.']);
});

test('without a previous release or anything new, the newest day is used', () => {
  assert.deepEqual(newEntries(changelog, null), ['New thing.', 'Wrapped entry that continues here.']);
  assert.deepEqual(newEntries(changelog, changelog), ['New thing.', 'Wrapped entry that continues here.']);
});

test('play notes keep whole entries within the limit', () => {
  assert.equal(playNotes(['aaaa', 'bbbb', 'cccc'], 13), '• aaaa\n• bbbb');
  assert.equal(playNotes(['a'.repeat(20)], 10), `• ${'a'.repeat(7)}…`);
  assert.ok(playNotes(Array(100).fill('x'.repeat(40))).length <= 500);
});

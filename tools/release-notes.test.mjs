// Tests of the release-notes tooling: node --test tools/*.test.mjs
import assert from 'node:assert/strict';
import { mkdtempSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { pendingNotes } from './release-notes.mjs';

test('release body: the notes are found before and after they are archived', () => {
  const dir = mkdtempSync(join(tmpdir(), 'notes-'));
  try {
    writeFileSync(join(dir, 'next.md'), 'New\n- a\n');
    assert.equal(pendingNotes('v1.2.0', dir), join(dir, 'next.md'));
    renameSync(join(dir, 'next.md'), join(dir, 'v1.2.0.md'));
    assert.equal(pendingNotes('v1.2.0', dir), join(dir, 'v1.2.0.md'));
    assert.equal(pendingNotes('v1.3.0', dir), undefined);
    assert.equal(pendingNotes(undefined, dir), undefined);
  } finally {
    rmSync(dir, { recursive: true });
  }
});

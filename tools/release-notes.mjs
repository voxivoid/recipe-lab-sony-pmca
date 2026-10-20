#!/usr/bin/env node
// The release notes people read on the Releases page, from one file written with the /release-notes skill.
//
//   node tools/release-notes.mjs show [vX.Y.Z]          the notes as markdown (next.md without a version)
//   node tools/release-notes.mjs github-start|github-end the release body around the generated commit list (.releaserc.js,
//                                                       dev-build.yml)
//   node tools/release-notes.mjs github vX.Y.Z           that release's body as it should be, from its notes and current body
//   node tools/release-notes.mjs archive X.Y.Z           release-notes/next.md -> release-notes/vX.Y.Z.md (git mv)
//
// release-notes/next.md is the notes of the release being prepared; once released, release-notes/vX.Y.Z.md.
// Plain text: a heading line ("New", "Changed", "Fixed", "Please try") and "- " lines under it.
//
// A release with no notes file keeps the generated commit list alone, as before.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'release-notes');
const NEXT = join(DIR, 'next.md');

/** The notes as markdown: heading lines become ### headings, "- " lines stay bullets. */
export function markdown(text) {
  return text
    .trim()
    .split(/\r?\n/)
    .map((l) => (l.trim() && !l.startsWith('- ') && !l.startsWith('  ') && !l.startsWith('#') ? `### ${l.trim()}` : l))
    .join('\n');
}

/** The notes file of a release (vX.Y.Z), or next.md without one. */
export function load(tag) {
  const file = tag ? join(DIR, `${tag}.md`) : NEXT;
  if (!existsSync(file)) throw new Error(`${file} does not exist: write it with the /release-notes skill`);
  return readFileSync(file, 'utf8');
}

/** The part of a release body to keep below the notes, and what to call it. */
function rest(body) {
  const m = /<details>\s*<summary>([^<]*)<\/summary>\s*([\s\S]*?)\s*<\/details>\s*$/.exec(body);
  if (m) return { summary: m[1], text: m[2].trim() };
  const text = body.trim();
  // semantic-release's list opens with "## [X.Y.Z](compare link) (date)"; anything else was written by hand.
  return { summary: /^## \[/.test(text) ? 'Commits' : 'More', text };
}

const open = (summary) => `<details>\n<summary>${summary}</summary>\n\n`;

/** A release body: the reviewed notes first, the generated commit list (or older text) folded below. */
export function githubBody(notes, currentBody) {
  const { summary, text } = rest(currentBody);
  return text ? `${markdown(notes)}\n\n${open(summary)}${text}\n\n</details>\n` : `${markdown(notes)}\n`;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const [command, arg] = process.argv.slice(2);
  const tag = arg ? `v${arg.replace(/^v/, '')}` : undefined;
  if (command === 'show') {
    process.stdout.write(`${markdown(load(tag))}\n`);
  } else if (command === 'github-start') {
    // semantic-release joins the notes of its plugins in order: this opens the fold before the generated commit
    // list, github-end closes it after. No next.md: both print nothing and the body is the generated list alone.
    if (existsSync(NEXT)) process.stdout.write(`${markdown(load())}\n\n${open('Commits')}`);
  } else if (command === 'github-end') {
    if (existsSync(NEXT)) process.stdout.write('\n</details>\n');
  } else if (command === 'github') {
    if (!tag) throw new Error('usage: github vX.Y.Z');
    const current = execFileSync('gh', ['release', 'view', tag, '--json', 'body', '--jq', '.body'], { encoding: 'utf8' });
    process.stdout.write(githubBody(load(tag), current));
  } else if (command === 'archive') {
    if (!tag) throw new Error('usage: archive X.Y.Z');
    if (!existsSync(NEXT)) console.log('release-notes/next.md does not exist: nothing to archive');
    else execFileSync('git', ['mv', NEXT, join(DIR, `${tag}.md`)], { stdio: 'inherit' });
  } else {
    console.error('usage: release-notes.mjs show [vX.Y.Z] | github vX.Y.Z | github-start | github-end | archive X.Y.Z');
    process.exit(1);
  }
}

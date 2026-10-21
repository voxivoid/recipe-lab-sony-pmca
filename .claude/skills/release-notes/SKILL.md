---
name: release-notes
description: Drafts the release notes of Recipe Lab's next release into release-notes/next.md, from the commits since the last tag - short, plain lines for camera owners on the Releases page (New, Changed, Fixed, Please try). Use before a release, when the user asks for release notes, or to write notes for an old tag (release-notes/vX.Y.Z.md) and put them on its GitHub release.
argument-hint: "[vX.Y.Z to write notes for an old release; default the next release]"
---

# Release notes

The notes are the top of the GitHub release, above the generated commit list (folded under "Commits"), and of the
rolling `dev` prerelease. Readers are camera owners deciding whether to install: photographers, not developers.
`tools/release-notes.mjs` turns the file into the release body.

Scope for this run: $ARGUMENTS (empty = the next release, written to `release-notes/next.md`; a tag = `release-notes/<tag>.md`).

## Steps
1. Find the range: next release = `git describe --tags --abbrev=0 --match 'v*'` to `HEAD`; an old tag = the tag before
   it to that tag. `git log --format='%h %s%n%b' <range>`, leaving out `chore(release)`, `ci`, `test`, `chore`, `docs`,
   `build`.
2. Read the diff of anything the subject does not explain (`git show --stat`, the screens, keys or recipes touched).
   Group by what the user sees, not by commit: five commits on one screen are one line.
   Only two kinds of change go in: **what changes how the app is used** (new or changed functions, keys, screens,
   recipes, files on the card, languages) and **bugs a user would have hit** (a look that does not stick after a
   power cycle, wrong colours, WRITE FAILED, a key that does nothing, a crash).
   Leave out everything internal: docs, compatibility-table updates, refactors, tests, CI/build/release tooling,
   dependency bumps, licence, code structure, and fixes to bugs that never shipped in a release (check the fix is
   not for a feature added in this same range). A feature added and then reworked in the range is one line, the
   final behaviour.
3. Write the file in exactly this shape (plain text, no hashes, no commit prefixes like `feat(ui):`; no links except a bare
   `https://recipelab.io/...` page, which GitHub makes clickable):

```
New
- <what changed, in words a photographer uses: the key to press, the menu row, what they see>

Changed
- <what works differently now; anything a user must relearn, such as a moved key, goes here>

Fixed
- <what was broken, as the user saw it>

Please try
- <only for the next release: a change verified on an A6000 alone that may behave differently on other bodies.
  Say what to do and what to report: "On a NEX or RX100, pick X, power-cycle, check Y; report it on GitHub.">
```
   - Omit a heading with nothing under it. Leave "Please try" out of old releases.
   - Name keys and menus as the camera and the app show them (MENU, Fn, trash, centre button, hold MENU → Language).
   - No version numbers, no slot IDs, no class or file names except files the user handles on the card
     (`RECIPES/*.YML`, `RECIPLAB/REPORT.TXT`).
   - Nothing about the camera can be proven by a build: never write that something works on a body nobody tried.
4. Re-read the result against the rules above: any line a user could not notice or act on is removed, any line that
   no longer matches the code is fixed.
5. Show the user the notes and ask for edits. Do not commit: `/create-release` does, or the user.

## Old releases
For a tag (`/release-notes v1.4.0`) write `release-notes/v1.4.0.md` the same way. Its GitHub release then gets the
notes on top and the current body folded below (idempotent, safe to re-run):

```bash
node tools/release-notes.mjs github v1.4.0                                    # prints the new body; show it
node tools/release-notes.mjs github v1.4.0 > /tmp/body.md && gh release edit v1.4.0 --notes-file /tmp/body.md
```

Never edit a published release without the user's OK. Keep the current body somewhere before the first edit.

---
name: commit-and-pr
description: Commit with a conventional commit message, push, and open a GitHub pull request against main
argument-hint: "[message hint]"
---

# Commit and Open a GitHub Pull Request

Commit the current changes, push, and open a PR into `main` if one does not already
exist. The rules here are enforced by CI — see [docs/CONTRIBUTING.md](../../../docs/CONTRIBUTING.md).

> **Never merge the PR.** Every PR needs an approving review from a code owner. Open it and
> stop.

## Steps

### 1. Analyze changes

Run in parallel:

```bash
git status
git diff --cached                 # staged
git diff                          # unstaged
git log --oneline -5              # recent commits, for style
git branch --show-current
```

### 2. Create a branch (if on `main`)

**Never commit to `main`.** It is the only long-lived branch and every change reaches it
through a PR. If the current branch is `main`, create a work branch first.

Every branch needs a GitHub issue. If the change has no issue yet, create one:

```bash
gh issue create --title "<type>: <what>" --label "type: <type>" --milestone "<current milestone>"
```

Name the branch `<type>/<issue>-<kebab-slug>`, where the type prefix matches the commit type:

```
feat/123-brand-jump-top-dial
fix/131-wb-finetune-sign
docs/ refactor/ chore/ build/ ci/ perf/ test/   same shape
```

```bash
git switch -c <branch-name>
```

### 3. Stage changes

Stage specific files by name; avoid `git add -A` / `git add .`.

**Never stage:** `.env` or credentials, `*.apk`, `*.keystore` (the signing key is
irreplaceable), `node_modules/`, `out/`, `jni/platform/errno.h.updater_only`. All are
gitignored — if one shows up in `git status`, something is wrong; stop and say so.

### 4. Check the release notes

Read `release-notes/next.md` and make it match the code after this commit: add a line for a change a camera owner
would notice, fix or drop lines this commit makes wrong. Stage it with the commit, or say in the report that it was
checked and needs no change. Rules: `CLAUDE.md` (Release notes) and `.claude/skills/release-notes/SKILL.md`.

### 4b. Run the gates before committing

These are the same checks CI runs, and they take a second locally:

```bash
./tools/check-version.sh                       # manifest sane, no version mirrors
./tools/check-commit-msg.sh "<subject>"        # the message you are about to use
```

After committing, check the whole branch — this also warns about any commit that does not
reference the branch's issue:

```bash
./tools/check-commit-msg.sh --range origin/main..HEAD
```

If you touched a workflow, validate it too:

```bash
python3 -c "import yaml,glob;[yaml.safe_load(open(f)) for f in glob.glob('.github/workflows/*.yml')]"
```

### 5. Write the commit message

```
<type>(<scope>): <subject>

Why the change was needed, if it is not obvious from the subject.

Closes #<issue>
```

**Every commit must reference its issue.** Get the number from the branch name — it is the
number between the type prefix and the slug:

```bash
git branch --show-current | sed -nE 's|^[a-z]+/([0-9]+)-.*|\1|p'
```

Use `Closes #N` when the commit finishes the issue, `Refs #N` when it is one step of several.
On a branch with many commits, the last one closes and the rest reference. If the branch name
carries no number, the change has no issue — go back to step 2 and create one.

- **Types:** `feat` `fix` `docs` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- **Scopes** (optional): `ui` `input` `browser` `recipes` `tools` `build` `ci` `docs` `deps` `release`
- **Subject:** ≤ 72 characters, imperative, lowercase, no trailing period
- **Breaking change:** `!` after the scope plus a `BREAKING CHANGE:` footer
- **Do not put the issue number in the subject.** It goes in the `Closes #N` / `Refs #N`
  footer; GitHub appends the PR number to the subject itself on squash merge, and two bare
  `#N` in one subject is ambiguous.
- Always end with: `Co-Authored-By: Claude <noreply@anthropic.com>`

If the user passed an argument, use it as guidance for the message: $ARGUMENTS

### 6. Commit

```bash
git commit -m "$(cat <<'EOF'
type(scope): subject

Body explaining why.

Closes #123

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

### 7. Push

```bash
git rev-parse --abbrev-ref --symbolic-full-name @{u} 2>/dev/null
```

Not tracking → `git push -u origin <branch-name>`, otherwise `git push`.

The push triggers `ci.yml`: `build`, `commit-lint`, `version-consistency`.

### 8. Open the pull request

Check for an existing one first:

```bash
gh pr list --head "$(git branch --show-current)"
```

If none exists, create it. **The base is `main`** — it is the only long-lived branch.

**The PR title is the most important string in this workflow.** A squash merge leaves only
the title, so it becomes the commit on `main` *and* it is what semantic-release reads
to decide the next version:

| title type | release |
|---|---|
| `fix:` `perf:` `refactor:` | patch |
| `feat:` | minor |
| `feat!:` or `BREAKING CHANGE:` | major |
| `chore:` `docs:` `ci:` `test:` `build:` | **nothing at all** |

So pick the type for what the change *is*, and never title a feature `chore:`. The `pr-title`
check validates the format but cannot tell you the type is wrong.

**PR body** — two sections:

1. **Summary** — bullets, one line per change. Prefix anything callers must adapt to with
   **Breaking:**.
2. **Test plan** — a `- [ ]` checklist a reviewer can tick. Include the camera step whenever
   the change touches recipes, the settings store, input handling or live preview; a green
   build proves only that it compiles.

Wrap file paths, function names and commands in backticks.

```bash
gh pr create --base main \
  --title "<type>(<scope>): <subject>" \
  --assignee @me \
  --body "$(cat <<'EOF'
Closes #123

## Summary
- Add a top-dial fast path to the brand picker in `PickerView.java`.
- **Breaking:** `Recipes.GROUPS` entries now need a `brand` field.

## Test plan
- [ ] Install on an A6000: `pmca-console.py install -d native -f RecipeLab.apk`
- [ ] Turn the top dial in the browser, expect the brand to jump
- [ ] Power-cycle the camera and confirm the stored look survived
- [ ] `./tools/check-version.sh`
EOF
)"
```

Branch deletion on merge is a repository setting, so no flag is needed.

If an existing PR was found instead, update its title to match the work when it has drifted:

```bash
gh pr edit <number> --title "<type>(<scope>): <subject>"
```

### 9. Testing on the camera

There is no way to screenshot this UI from CI — it runs on the camera. If the change touches
recipes, settings-store IDs, input handling or live preview, either test it on an A6000 and
say so in the PR, or label the issue so it is not forgotten:

```bash
gh issue edit <issue> --add-label "needs-on-camera-verification"
```

The install path for a PR build is the workflow artifact, or `RecipeLab-dev.apk` from the
rolling [`dev` prerelease](https://github.com/voxivoid/recipe-lab-sony-pmca/releases/tag/dev)
once the change is on `main`.

### 10. Check CI

```bash
gh run list --branch "$(git branch --show-current)" --limit 2
```

If `build` fails, read the failure before reporting success:

```bash
gh run view <run-id> --log-failed
```

### 11. Report

Tell the user:

- what was committed, and the commit hash
- the PR URL
- which CI checks passed or failed
- **what release the PR title implies** (patch / minor / major / none) — so a mistyped title
  is caught before merge
- whether the change still needs verifying on a camera
- whether `release-notes/next.md` changed, or was checked and needs nothing

/**
 * The version is decided from commit messages; AndroidManifest.xml is written by
 * tools/bump-version.sh, which also computes versionCode from the formula.
 *
 * There is no semantic-release plugin for a bare AndroidManifest.xml (Flutter projects
 * use semantic-release-pub for this), so @semantic-release/exec drives our own script.
 */
export default {
  branches: [{ name: 'main' }],

  plugins: [
    [
      '@semantic-release/commit-analyzer',
      {
        preset: 'conventionalcommits',
        releaseRules: [{ type: 'refactor', release: 'patch' }],
      },
    ],
    // The release's body is the reviewed notes (release-notes/next.md) with the generated commit list folded below.
    // semantic-release joins the notes of its plugins in order, so the first exec opens a <details>, the second closes it.
    ['@semantic-release/exec', { generateNotesCmd: 'node tools/release-notes.mjs github-start' }],
    [
      '@semantic-release/release-notes-generator',
      {
        preset: 'conventionalcommits',
        presetConfig: {
          types: [
            { type: 'feat', section: 'New' },
            { type: 'fix', section: 'Fixed' },
            { type: 'perf', section: 'Performance' },
            { type: 'refactor', section: 'Refactor' },
            { type: 'docs', section: 'Documentation' },
            { type: 'build', section: 'Build' },
            { type: 'ci', hidden: true },
            { type: 'chore', hidden: true },
            { type: 'test', hidden: true },
          ],
        },
      },
    ],
    ['@semantic-release/exec', { generateNotesCmd: 'node tools/release-notes.mjs github-end' }],
    [
      '@semantic-release/exec',
      {
        // Writes versionName + versionCode, then builds the APK that gets published.
        // The versioned copy is what people keep; RecipeLab.apk keeps the stable
        // releases/latest/download URL the README points at working.
        prepareCmd:
          './tools/bump-version.sh ${nextRelease.version}' +
          // The notes written with /release-notes become this release's file, in the same release commit.
          ' && node tools/release-notes.mjs archive ${nextRelease.version}' +
          ' && RELEASE=1 RELEASE_TAG=v${nextRelease.version} ./build.sh' +
          ' && cp RecipeLab.apk RecipeLab-${nextRelease.version}.apk',
      },
    ],
    [
      '@semantic-release/github',
      {
        assets: [
          {
            path: 'RecipeLab-${nextRelease.version}.apk',
            label: 'RecipeLab ${nextRelease.version} (APK)',
          },
          {
            path: 'RecipeLab.apk',
            label: 'RecipeLab.apk — stable link, always the latest release',
          },
          {
            path: 'RecipeLab.apk.sha256',
            label: 'RecipeLab ${nextRelease.version} (SHA-256)',
          },
        ],
        successComment: false,
        failComment: false,
      },
    ],
    [
      '@semantic-release/git',
      {
        assets: ['AndroidManifest.xml', 'release-notes/v*.md'],
        message: 'chore(release): ${nextRelease.version} [skip ci]',
      },
    ],
  ],
};

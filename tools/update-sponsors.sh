#!/usr/bin/env bash
# Rewrites the sponsor list in README.md from the GitHub Sponsors API.
#
# The query returns public sponsors only -- a private sponsorship never appears in the
# response, so it cannot leak into the README. Sorted by login so an unchanged set of
# sponsors produces an unchanged file.
#
# Needs a token with `read:user`. The Actions GITHUB_TOKEN cannot read sponsorships at all
# (`INSUFFICIENT_SCOPES`), which is why the workflow passes a PAT instead. Locally:
#
#   GH_TOKEN="$(gh auth token)" ./tools/update-sponsors.sh
#
# and the same scope applies -- a `gh` login without `read:user` fails the same way.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

LOGIN="${SPONSORS_LOGIN:-voxivoid}"
README="README.md"
BEGIN="<!-- sponsors:begin -->"
END="<!-- sponsors:end -->"

grep -qF "$BEGIN" "$README" && grep -qF "$END" "$README" || {
  echo "FAIL: $README has no $BEGIN / $END block" >&2
  exit 1
}

# `name` is optional on a GitHub account; the list falls back to the login when it is unset.
LIST="$(gh api graphql -f login="$LOGIN" -f query='
  query($login: String!) {
    user(login: $login) {
      sponsors(first: 100) {
        nodes {
          ... on User         { login name }
          ... on Organization { login name }
        }
      }
    }
  }' --jq '
    [ .data.user.sponsors.nodes[]
      | select(.login != null)
      | { login, name: (if (.name // "") == "" then .login else .name end) } ]
    | sort_by(.login | ascii_downcase)
    | map("- [" + .name + " (@" + .login + ")](https://github.com/" + .login + ")")
    | join("\n")
  ')"

[ -n "$LIST" ] || LIST="_No sponsors yet._"

# awk rather than sed: the replacement is multi-line and holds characters sed would read.
awk -v begin="$BEGIN" -v end="$END" -v list="$LIST" '
  $0 == begin { print; print ""; print list; print ""; skip = 1; next }
  $0 == end   { skip = 0 }
  !skip
' "$README" > "$README.tmp"

if cmp -s "$README.tmp" "$README"; then
  rm -f "$README.tmp"
  echo "sponsors: no change"
else
  mv "$README.tmp" "$README"
  echo "sponsors: $README updated"
fi

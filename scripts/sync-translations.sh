#!/usr/bin/env bash
# Moves the app's text to and from the web app's repo, where Crowdin translates it.
# Crowdin can only watch one repo, so the English strings.xml lives there as a copy.
#
#   scripts/sync-translations.sh push   copy the English text to the web repo
#   scripts/sync-translations.sh pull   copy the translations back from it
#
# The web repo is expected next to this one; set WEB_REPO to use another path.
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
web="${WEB_REPO:-$here/../Nextcloud ShoppingList}"
res="$here/app/src/main/res"
mirror="$web/android"

[ -d "$web/.git" ] || { echo "web repo not found at $web (set WEB_REPO)" >&2; exit 1; }

case "${1:-}" in
  push)
    mkdir -p "$mirror/values"
    cp "$res/values/strings.xml" "$mirror/values/strings.xml"
    echo "copied the English text to $mirror/values/strings.xml, commit it in the web repo"
    ;;
  pull)
    count=0
    for dir in "$mirror"/values-*/; do
      [ -f "$dir/strings.xml" ] || continue
      name="$(basename "$dir")"
      mkdir -p "$res/$name"
      cp "$dir/strings.xml" "$res/$name/strings.xml"
      echo "  $name"
      count=$((count + 1))
    done
    echo "copied $count translation(s) into app/src/main/res"
    ;;
  *)
    echo "usage: $0 push|pull" >&2
    exit 2
    ;;
esac

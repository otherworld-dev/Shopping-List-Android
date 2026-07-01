# Shopping List for Nextcloud — Android

The **official** Android client for the [Shopping List](https://github.com/otherworld-dev/Shopping-List)
server app for Nextcloud. A fast, shared, offline-first shopping list that syncs through your own
Nextcloud server — no third-party cloud, no accounts, no trackers.

> An independent app made by Otherworld. Not affiliated with or endorsed by Nextcloud GmbH;
> "Nextcloud" is a trademark of its owner.

## Features

- Shared lists that update live from your Nextcloud
- Items grouped into colour-coded shop areas (aisles)
- Smart input — parses quantities, picks the area, merges duplicates
- Works offline; syncs when you're back online
- Drag to reorder; share with Nextcloud users, groups, or a public link

## Requirements

- Android 8.0+ (minSdk 26)
- A Nextcloud server (v30–35) with the [Shopping List server app](https://apps.nextcloud.com/apps/shopping_list) installed

## Install

- **F-Droid** — coming soon (free)
- **Google Play** — coming soon (paid, for the convenience of automatic updates)
- **APK** — from the [Releases](https://github.com/otherworld-dev/Shopping-List-Android/releases) page

## Build

```sh
./gradlew assembleRelease
```

Release signing is read from a `keystore.properties` file at the repo root (git-ignored); without it,
the release build is produced unsigned (as F-Droid builds it).

## Privacy

The app collects nothing. Your data lives only on your device and the Nextcloud server you choose.
See the [privacy policy](https://shoppinglist.otherworld.dev/privacy.html).

## License

Copyright © 2026 Otherworld Dev Ltd.

Licensed under the **GNU Affero General Public License v3.0 or later** (AGPL-3.0-or-later).
See [LICENSE](LICENSE) for the full text.

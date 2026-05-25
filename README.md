# SpoolLink

SpoolLink is a companion app for [Spoolman](https://github.com/Donkie/Spoolman) that links existing NFC
tags to filament spools for use with Snapmaker U1. It does not create custom NFC tags; it records and
manages tag UIDs in Spoolman so a tag is assigned to only one spool at a time. For writing tags, use
other applications from the relevant app marketplaces.

## How it works

1. Scan any NFC tag — blank tags and OpenSpool-format tags are both supported.
2. Assign the tag to an existing Spoolman spool, or create a new one. If the tag carries
   OpenSpool data, the filament fields are pre-filled automatically.
3. The card's UID is appended to the spool's `card_uids` custom field in Spoolman, and
   removed from any other spool it was previously linked to.

A tag can be reassigned or unlinked at any time by scanning it again. Spools work best
with two tags — one on each side — so the spool is detected regardless of orientation.

## Apps

| Platform | Location | Language | Guide |
|----------|----------|----------|-------|
| iOS | `ios-app/` | Swift / SwiftUI | [User Guide](docs/IOS.md) |
| Web | `web-app/` | Plain HTML + JS | |

## Running
**iOS** — open `ios-app/NFCSpoolReader.xcodeproj` in Xcode and run on a physical device.
Requires a paid Apple Developer subscription — NFC entitlements are not available with a free
account.

**Web** — open `web-app/index.html` directly, or serve it with any static file server.
Requires Spoolman to be accessible over HTTPS and have CORS configured to allow the page's origin.

## Documentation

- [`docs/DESIGN.md`](docs/DESIGN.md) — UI/UX design guidelines: colors, button styles, component
  patterns, and screen layouts for both iOS and Android.
- [`docs/SPOOLMAN.md`](docs/SPOOLMAN.md) — Spoolman API protocol reference: all endpoints, field
  formats, NFC tag schema, and sync logic.
- [`docs/IOS.md`](docs/IOS.md) — iOS user guide.


## Project structure

```
ios-app/       iOS app (Swift / SwiftUI)
web-app/       Web app (HTML + JS)
docs/          Design and API reference documentation
icons/         App icon source files
```

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

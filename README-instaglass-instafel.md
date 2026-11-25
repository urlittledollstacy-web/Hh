# InstaGlass — Instafel build

> This build and README were produced by an AI agent (OpenHands) on behalf of the repository owner.

Patched fork of [maciasdav/InstaGlass](https://github.com/maciasdav/InstaGlass) v1.0.2 (MIT) retargeted from
`com.instagram.android` to **`com.instafel.android`**, so the liquid-glass tab bar works on Instafel.

## Download

`InstaGlass-instafel-v1.0.2.apk` — 3.9 MB

```
sha256 d9c18b4baaaa9f1959c3c04447a725ecba43c7735cd2d8690c8285d2bc39fcb2
```

## Install

```bash
su -c 'pm uninstall com.maciasdxv.igglass'   # signature differs from upstream, remove first
su -c 'pm install -r InstaGlass-instafel-v1.0.2.apk'
```

1. Vector / LSPosed: enable **InstaGlass**, scope **`com.instafel.android`**
2. Open InstaGlass → **Restart Instagram** (force-stops Instafel)
3. Grant su for the restart button

## Patch applied (6 edits)

| File | Change |
|---|---|
| `HookEntry.kt` | `IG_PACKAGE` + `processName` gate |
| `TabBarGlass.kt` | `IG_PACKAGE` for resource-ID lookups |
| `MainActivity.kt` | open-intent package + `am force-stop` (Restart button) |
| `StatusCard.kt` | package for version detection |
| `res/values/arrays.xml` | `xposed_scope` |
| `AndroidManifest.xml` | `<queries>` package |

Unchanged: `TAB_NAMES` (`feed_tab`, `clips_tab`, `direct_tab`, `search_tab`, `profile_tab`) — Instafel preserves Instagram's resources.

## Build

AGP 8.10.1 · Kotlin 2.4.0 · Gradle 8.11.1 · JDK 21 · compileSdk 37

```bash
./gradlew assembleRelease
zipalign -f 4 app-release.apk aligned.apk
apksigner sign --ks ig.keystore --out InstaGlass-instafel-v1.0.2.apk aligned.apk
```

Signing key: self-generated (`CN=InstaGlass Instafel Build`), not upstream's. Future rebuilds must use the same key to install over this one.

## Caveats

- Instafel is a re-signed Instagram client; account risk is unchanged by this module.
- Upstream InstaGlass is ~3 days old at time of build; breakage on Instagram Alpha updates is expected.
- `processName` gate requires Instafel's UI to run in the main process.

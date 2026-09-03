# kRadar

<img src="docs/icon.png" width="96" align="right" alt="kRadar icon" />

A single-screen **precipitation radar** for the [Mudita Kompakt](https://mudita.com/)
e-ink phone (MuditaOS-K, AOSP, **no Google Services**). It overlays
[RainViewer](https://www.rainviewer.com/) European radar imagery on a static
vector map (country borders + cities), centered on your current GPS position,
with a ~2 hour animated history and +/- zoom.

## What it does

- Centers on your location using AOSP `LocationManager` (GPS, then network) —
  **not** FusedLocationProviderClient, which needs Play Services.
- Downloads RainViewer's "widget" tiles (Web Mercator, centered on lat/lon) for
  every past frame at once, quantizes each to discrete grey intensity levels for
  e-ink, then animates them in memory (no network during playback).
- Draws borders + cities as a static vector layer using the **same Web Mercator
  projection** as the tiles, so map and radar stay aligned at every zoom.
- Shows the mandatory "Weather data by RainViewer" attribution on screen.

## Data source & attribution

- Radar: **RainViewer Weather Maps API** — free for personal use, no API key.
  Attribution "Weather data by RainViewer" is displayed in-app (required).
  <https://www.rainviewer.com/api/weather-maps-api.html>
- Base map data is converted from the **MeteoPlaneRadar** project:
  borders = **Natural Earth** (public domain), cities = **GeoNames** (CC BY 4.0).

## Map data (regenerating the assets)

`app/src/main/assets/{borders.json,cities.json}` are generated from the sibling
`MeteoPlaneRadar` project. To regenerate:

```bash
python3 tools/convert_mapdata.py
# or point at a different checkout:
METEOPLANE_SRC=/path/to/MeteoPlaneRadar/src python3 tools/convert_mapdata.py
```

- `borders.json` — `[[[lat,lon], ...], ...]` (one array per country ring).
- `cities.json`  — `[{"name","lat","lon","tier"}, ...]` (Czech list overrides EU
  inside the CZ bounding box).

## Build & install

Requires JDK 17+ (the Android Studio JBR works). The Android SDK path goes in
`local.properties` (copy from `local.properties.example`).

```bash
export JAVA_HOME=/opt/android-studio/jbr     # or any JDK 17+
./gradlew assembleDebug                       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                     # signed release (needs keystore in local.properties)
```

Sideload the APK via **Mudita Center** (USB-C), WebADB, or `adb install`.
For release updates, always reuse the same keystore or installs fail with
"signatures do not match".

### Cutting a release

Locally, `scripts/build-release.sh` builds a signed, minified APK and names it
`kradar-<versionName>.apk` (≈2 MB) in the project root:

```bash
./scripts/build-release.sh
```

In CI, `.github/workflows/release.yml` builds and attaches that APK to a GitHub
Release when a `v*` tag is pushed:

```bash
git tag v1.0 && git push origin v1.0
```

The workflow needs four repository secrets (Settings → Secrets and variables →
Actions), so the keystore never lives in the repo:

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | `base64 -w0 keystore/kradar.jks` |
| `KEYSTORE_PASSWORD` | store password |
| `KEY_ALIAS` | `kradar` |
| `KEY_PASSWORD` | key password |

## Tuning knobs

All isolated to single constants:

- `render/EinkConverter.kt` — `NUM_LEVELS`, `MASK_THRESHOLD`, alpha ramp
  (`ALPHA_MIN`/`ALPHA_MAX`).
- `ui/RadarViewModel.kt` — `playbackIntervalMs`.
- `ui/RadarUiState.kt` — `DEFAULT_ZOOM`, `MIN_ZOOM`/`MAX_ZOOM`, `TILE_SIZE`.

## Architecture

```
net/RainViewerClient.kt   metadata (weather-maps.json) + tile download (OkHttp)
render/EinkConverter.kt   PNG -> quantized grey overlay (intensity from colour)
map/MapProjection.kt      Web Mercator, matches RainViewer tiles
map/MapData.kt            loads borders/cities from JSON assets
location/LocationProvider AOSP LocationManager (no Play Services)
ui/RadarViewModel.kt      prefetch, in-memory bitmap cache, playback state machine
ui/MeteoRadarScreen.kt    static vector layer + dynamic overlay + MMD controls
```

## License

kRadar is licensed under **GPL-3.0** — see [`LICENSE`](LICENSE). The in-app About
dialog (open it with the ⓘ button, top-right of the screen) shows the version,
license, data credits, and links to the [GitHub repo](https://github.com/ok1cdj/kRadar)
and Buy Me a Coffee. Bundled data keeps its own licenses: borders © Natural Earth
(public domain), cities © GeoNames (CC BY 4.0); radar © RainViewer.

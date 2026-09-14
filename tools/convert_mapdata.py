#!/usr/bin/env python3
"""Build kRadar's global vector base map from Natural Earth GeoJSON.

Reads (downloaded from Natural Earth, cached under tools/.ne_cache/):
  - ne_110m_admin_0_countries.geojson : country polygons -> borders/coastlines
  - ne_50m_populated_places.geojson   : populated places -> city labels

Optionally overlays (from the sibling MeteoPlaneRadar project, if present):
  - CzCitiesData.h : CZ_CITIES[] + CZ bounding box. Inside that box the curated
                     Czech list replaces the global cities, so the home region
                     keeps human-friendly names/abbreviations (PHA, OVA, PLZ ...).

Writes (into app/src/main/assets/):
  - borders.json : [[[lat,lon],[lat,lon], ...], ...]   one array per ring/polyline
  - cities.json  : [{"name","abbr","lat","lon","minZoom"}, ...]

Each city carries a "minZoom": the lowest app zoom (4..7) at which it appears, so
the map reveals more places as you zoom in. It is derived from Natural Earth's
SCALERANK (label importance), with capitals / megacities promoted to show earlier.
The app draws a city when minZoom <= currentZoom; important cities (minZoom <= 5)
get their full name, the rest get the abbreviation.

Data sources: Natural Earth (public domain, https://www.naturalearthdata.com),
via the nvkelso/natural-earth-vector GeoJSON mirror. Czech overlay: MeteoPlaneRadar.
"""

import json
import os
import re
import sys
import urllib.request

# --- paths ----------------------------------------------------------------
HERE = os.path.dirname(os.path.abspath(__file__))
CACHE_DIR = os.environ.get("NE_CACHE", os.path.join(HERE, ".ne_cache"))
OUT_DIR = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "assets"))

# Sibling MeteoPlaneRadar project — only needed for the optional CZ overlay.
SRC_DIR = os.environ.get(
    "METEOPLANE_SRC",
    os.path.normpath(os.path.join(HERE, "..", "..", "MeteoPlaneRadar", "src")),
)
CZ_MAP = os.path.join(SRC_DIR, "CzCitiesData.h")

# --- Natural Earth inputs -------------------------------------------------
NE_BASE = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson"
BORDERS_SRC = "ne_50m_admin_0_countries.geojson"    # medium detail: smooth coasts at zoom 4-7
CITIES_SRC = "ne_10m_populated_places.geojson"      # ~7300 places, dense worldwide coverage

# --- city zoom reveal (app zoom range is 4..7) ----------------------------
MIN_ZOOM = 4
MAX_ZOOM = 7


def load_geojson(name):
    """Return parsed GeoJSON, downloading to the cache on first use."""
    path = os.path.join(CACHE_DIR, name)
    if not os.path.exists(path):
        os.makedirs(CACHE_DIR, exist_ok=True)
        url = f"{NE_BASE}/{name}"
        print(f"downloading {name} ...")
        urllib.request.urlretrieve(url, path)
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


# --- borders --------------------------------------------------------------
def _rings_of(geom):
    """Yield each linear ring ([[lon,lat], ...]) of a Polygon/MultiPolygon."""
    t = geom["type"]
    if t == "Polygon":
        for ring in geom["coordinates"]:
            yield ring
    elif t == "MultiPolygon":
        for poly in geom["coordinates"]:
            for ring in poly:
                yield ring


def parse_borders(gj):
    """Country polygons -> list of rings of [lat, lon] (whole world)."""
    rings = []
    for feat in gj["features"]:
        geom = feat.get("geometry")
        if not geom:
            continue
        for ring in _rings_of(geom):
            out = [[round(lat, 4), round(lon, 4)] for lon, lat in ring]
            if len(out) >= 2:
                rings.append(out)
    return rings


# --- cities ---------------------------------------------------------------
def min_zoom_of(props):
    """Lowest app zoom (4..7) at which a place is shown.

    SCALERANK is Natural Earth's label-importance rank (0 = most prominent). We
    bucket it into the app's four zoom levels, then promote big/capital cities so
    they surface earlier regardless of rank.
    """
    sr = props.get("SCALERANK")
    sr = 10 if sr is None else sr
    if sr <= 1:
        z = 4
    elif sr <= 3:
        z = 5
    elif sr <= 6:
        z = 6
    else:
        z = 7

    pop = props.get("POP_MAX") or 0
    cap = props.get("ADM0CAP") in (1, 1.0)
    mega = props.get("MEGACITY") in (1, 1.0)
    if mega or pop >= 5_000_000:
        z = min(z, MIN_ZOOM)          # world-class cities always visible
    elif cap or pop >= 1_000_000:
        z = min(z, MIN_ZOOM + 1)      # capitals / millionaires one step later
    return z


def parse_ne_cities(gj):
    """Populated places -> city dicts tagged with a per-city minZoom."""
    out = []
    for feat in gj["features"]:
        p = feat.get("properties", {})
        name = p.get("NAMEASCII") or p.get("NAME")
        if not name:
            continue
        lat = p.get("LATITUDE")
        lon = p.get("LONGITUDE")
        if lat is None or lon is None:
            lon, lat = feat["geometry"]["coordinates"][:2]
        out.append(
            {
                "name": name,
                "lat": round(float(lat), 4),
                "lon": round(float(lon), 4),
                "minZoom": min_zoom_of(p),
            }
        )
    return out


# Hand-curated abbreviations for well-known cities. Names match the ASCII
# spelling from Natural Earth (NAMEASCII). Everything not listed falls back to
# abbr_from below. (Czech cities keep MeteoPlaneRadar's own curated abbrs.)
CURATED_ABBR = {
    # Capitals / very large European cities
    "London": "LON", "Berlin": "BER", "Madrid": "MAD", "Rome": "ROM",
    "Paris": "PAR", "Bucharest": "BUC", "Budapest": "BUD", "Warsaw": "WAW",
    "Vienna": "WIE", "Barcelona": "BCN", "Stockholm": "STO", "Milan": "MIL",
    "Munich": "MUC", "Copenhagen": "CPH", "Sofia": "SOF", "Hamburg": "HAM",
    "Amsterdam": "AMS", "Dublin": "DUB", "Lisbon": "LIS", "Athens": "ATH",
    "Brussels": "BRU", "Helsinki": "HEL", "Oslo": "OSL", "Zagreb": "ZAG",
    "Belgrade": "BEG", "Bratislava": "BTS", "Ljubljana": "LJU", "Riga": "RIG",
    "Vilnius": "VNO", "Tallinn": "TLL", "Luxembourg": "LUX", "Zurich": "ZUR",
    "Geneva": "GVA", "Bern": "BRN", "Cologne": "CGN", "Frankfurt": "FRA",
    "Stuttgart": "STR", "Dusseldorf": "DUS", "Dresden": "DRS", "Leipzig": "LEJ",
    "Manchester": "MAN", "Birmingham": "BIR", "Glasgow": "GLA", "Edinburgh": "EDI",
    "Marseille": "MRS", "Lyon": "LYO", "Naples": "NAP", "Turin": "TRN",
    "Krakow": "KRK", "Gdansk": "GDN", "Thessaloniki": "SKG", "Gothenburg": "GOT",
    "Rotterdam": "RTM", "Porto": "OPO", "Valencia": "VLC", "Seville": "SEV",
    # Major US cities
    "New York": "NYC", "Los Angeles": "LA", "Chicago": "CHI",
    "Houston": "HOU", "Phoenix": "PHX", "Philadelphia": "PHL",
    "San Antonio": "SAT", "San Diego": "SD", "Dallas": "DAL",
    "San Francisco": "SF", "Seattle": "SEA", "Denver": "DEN",
    "Washington, D.C.": "DC", "Boston": "BOS", "Miami": "MIA",
    "Atlanta": "ATL", "Detroit": "DET", "Minneapolis": "MSP",
    "Las Vegas": "LV", "Oklahoma City": "OKC", "New Orleans": "NOLA",
    "Portland": "PDX", "Kansas City": "KC", "Salt Lake City": "SLC",
    # Other well-known world cities
    "Tokyo": "TYO", "Beijing": "BEJ", "Shanghai": "SHA", "Delhi": "DEL",
    "Mumbai": "BOM", "Moscow": "MOW", "Istanbul": "IST", "Cairo": "CAI",
    "Sydney": "SYD", "Melbourne": "MEL", "Toronto": "TOR", "Montreal": "YUL",
    "Mexico City": "MEX", "Sao Paulo": "SAO", "Rio de Janeiro": "RIO",
    "Buenos Aires": "BA", "Singapore": "SIN", "Hong Kong": "HK",
    "Dubai": "DXB", "Bangkok": "BKK", "Seoul": "SEL",
}


def abbr_from(name):
    """Readable 3-4 letter fallback: drop parentheticals, prefer the main word."""
    n = re.sub(r"\(.*?\)", "", name).strip()
    parts = [re.sub(r"[^A-Za-z]", "", p) for p in re.split(r"[ \-/]+", n)]
    parts = [p for p in parts if p]
    if not parts:
        return re.sub(r"[^A-Za-z]", "", name)[:4].upper() or "?"
    base = parts[0]
    if len(base) < 3 and len(parts) > 1:      # "A Coruna" -> "ACOR"
        base = base + parts[1]
    return base[:4].upper()


def curate_abbr(cities):
    """Attach a curated or generated readable abbreviation to each city."""
    for c in cities:
        c["abbr"] = CURATED_ABBR.get(c["name"], abbr_from(c["name"]))
    return cities


# --- optional Czech overlay (from MeteoPlaneRadar) ------------------------
# struct EuCity { const char* name; const char* abbr; float lon, lat; uint8_t tier; };
CITY_RE = re.compile(
    r'\{\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,'
    r"\s*(-?\d+(?:\.\d+)?)f?\s*,\s*(-?\d+(?:\.\d+)?)f?\s*,\s*(\d+)\s*\}"
)


def slice_array(text, decl):
    """Return the text between the '{' after `decl` and its matching outer '}'."""
    start = text.index(decl)
    brace = text.index("{", start)
    depth = 0
    for i in range(brace, len(text)):
        c = text[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return text[brace + 1 : i]
    raise ValueError(f"unterminated array for {decl!r}")


# Curated Czech cities keep human abbreviations; map their 1/2 tier to a minZoom
# so the home region reveals its big cities early and smaller towns a step later.
CZ_TIER_MINZOOM = {1: MIN_ZOOM + 1, 2: MIN_ZOOM + 2}


def parse_c_cities(body):
    out = []
    for m in CITY_RE.finditer(body):
        name, abbr, lon, lat, tier = m.groups()
        out.append(
            {
                "name": name,
                "abbr": abbr,
                "lat": round(float(lat), 4),
                "lon": round(float(lon), 4),
                "minZoom": CZ_TIER_MINZOOM.get(int(tier), MAX_ZOOM),
            }
        )
    return out


def parse_cz_box(text):
    def val(key):
        return float(re.search(rf"#define\s+{key}\s+(-?\d+(?:\.\d+)?)f?", text).group(1))
    return {
        "lat0": val("CZ_BOX_LAT0"),
        "lat1": val("CZ_BOX_LAT1"),
        "lon0": val("CZ_BOX_LON0"),
        "lon1": val("CZ_BOX_LON1"),
    }


def apply_cz_overlay(cities):
    """If the MeteoPlaneRadar CZ list is available, replace global cities inside
    the CZ bounding box with the curated Czech ones. Returns cities unchanged
    (with a warning) when the source header is missing."""
    if not os.path.exists(CZ_MAP):
        print(f"note: {CZ_MAP} not found — emitting global cities only "
              f"(set METEOPLANE_SRC to add the Czech overlay).")
        return cities
    cz_text = open(CZ_MAP, "r", encoding="utf-8", errors="replace").read()
    cz_cities = parse_c_cities(slice_array(cz_text, "CZ_CITIES"))
    box = parse_cz_box(cz_text)

    def in_box(c):
        return box["lat0"] <= c["lat"] <= box["lat1"] and box["lon0"] <= c["lon"] <= box["lon1"]

    kept = [c for c in cities if not in_box(c)]
    print(f"CZ overlay: replaced {len(cities) - len(kept)} global cities inside the "
          f"CZ box with {len(cz_cities)} curated ones.")
    return kept + cz_cities


def main():
    rings = parse_borders(load_geojson(BORDERS_SRC))
    cities = curate_abbr(parse_ne_cities(load_geojson(CITIES_SRC)))
    cities = apply_cz_overlay(cities)

    os.makedirs(OUT_DIR, exist_ok=True)
    borders_path = os.path.join(OUT_DIR, "borders.json")
    cities_path = os.path.join(OUT_DIR, "cities.json")
    with open(borders_path, "w", encoding="utf-8") as f:
        json.dump(rings, f, separators=(",", ":"))
    with open(cities_path, "w", encoding="utf-8") as f:
        json.dump(cities, f, ensure_ascii=False, separators=(",", ":"))

    n_pts = sum(len(r) for r in rings)
    print(f"borders.json: {len(rings)} rings, {n_pts} points "
          f"({os.path.getsize(borders_path) / 1024:.0f} KB)")
    by_z = {z: sum(1 for c in cities if c['minZoom'] == z) for z in range(MIN_ZOOM, MAX_ZOOM + 1)}
    print(f"cities.json : {len(cities)} cities, by minZoom {by_z} "
          f"({os.path.getsize(cities_path) / 1024:.0f} KB)")
    print(f"written to {OUT_DIR}")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001 — CLI tool, surface a readable message
        sys.exit(f"error: {e}")

#!/usr/bin/env python3
"""Chiede a Open-Meteo quali modelli esistono, cosa coprono, e cosa manca.

Serve perche' la domanda "quale modello e' piu' preciso qui" non si risponde a
memoria: i modelli vengono aggiunti e ritirati, e un modello ad alta risoluzione
**accettato** non e' lo stesso di un modello che ha davvero dei valori sopra
Noceto. Il container di sviluppo non raggiunge api.open-meteo.com, la CI si:
questo gira li' e pubblica il risultato su ci-artifacts.

La chiamata piu' informativa e' la prima: si chiede un modello inesistente
apposta, e l'errore che torna elenca tutti quelli accettati.

**Il giro mondiale.** Open-Meteo non e' un modello: e' un aggregatore, e
`best_match` sceglie da se' il modello nazionale del posto - ICON del DWD
sull'Europa centrale, AROME sulla Francia, JMA sul Giappone, GFS/HRRR sugli
Stati Uniti. Cioe' l'app usa gia' JMA a Tokyo, **e nessuno l'aveva mai
verificato**: era memoria, e qui la memoria non vale come risposta.

Le due domande a cui il giro risponde:

1. **quale modello risponde davvero** sotto `best_match`, ricavato per
   confronto: si chiede la stessa ora ai candidati e si guarda chi da' gli
   stessi identici numeri. L'API il nome non lo dichiara, quindi si deduce
   invece di crederci;
2. **quali variabili tornano nulle**, che e' la piu' importante per chi
   disegna. Una serie assente non e' un errore: e' una colonna di `null` che
   un grafico disegna come una linea a zero, cioe' come una previsione di
   niente. Chi la disegna deve poterlo sapere prima, non scoprirlo da uno
   scatto.
"""

import json
import os
import urllib.error
import urllib.request

BASE = "https://api.open-meteo.com/v1/forecast"

# Due punti, uno per capo della regione: se un modello copre l'uno e non
# l'altro, si vede subito.
PLACES = [
    ("Noceto", 44.80, 10.18),
    ("Forli", 44.22, 12.04),
]

# I posti che l'app offre gia' come scorciatoie, piu' uno oltreoceano: servono a
# sapere **come si rompe** un modello regionale fuori dal suo dominio. Non e' una
# curiosita': decide se la scelta del modello puo' essere un'impostazione fissa o
# se deve sapersi ritirare da sola.
ABROAD = [
    ("Londra", 51.51, -0.13),
    ("Bergen", 60.39, 5.32),
    ("Singapore", 1.35, 103.82),
    ("NewYork", 40.71, -74.01),
]

REGIONAL = ["italia_meteo_arpae_icon_2i", "icon_d2", "meteofrance_seamless"]

# ── Il giro mondiale ────────────────────────────────────────────────────────
#
# Un punto per continente e per regime, scelti perche' ciascuno mette alla
# prova qualcosa di diverso: Tokyo per JMA, New York per il NWS, Oslo e
# Reykjavik per l'estremo nord dove i modelli globali si diradano, Nairobi e
# Citta' del Capo per l'emisfero sud e per l'Africa, che nella lista delle
# fonti nazionali e' quasi tutta scoperta, Sydney e San Paolo per l'altro capo
# del mondo, Delhi e Singapore per i tropici, dove la pioggia convettiva e' la
# grandezza che i modelli sbagliano di piu'.
WORLD = [
    ("Tokyo", 35.68, 139.69),
    ("NewYork", 40.71, -74.01),
    ("Oslo", 59.91, 10.75),
    ("Reykjavik", 64.15, -21.94),
    ("Nairobi", -1.29, 36.82),
    ("CittaDelCapo", -33.92, 18.42),
    ("Sydney", -33.87, 151.21),
    ("SanPaolo", -23.55, -46.63),
    ("Delhi", 28.61, 77.21),
    ("Singapore", 1.35, 103.82),
    ("Noceto", 44.80, 10.18),
]

# I modelli nazionali che `best_match` puo' plausibilmente scegliere sui punti
# qui sopra. Servono a **dedurre** quale ha risposto: l'API il nome non lo
# dichiara, ma due modelli diversi non danno mai gli stessi identici decimali.
NATIONAL = [
    "jma_seamless",
    "gfs_seamless",
    "icon_seamless",
    "meteofrance_seamless",
    "metno_seamless",
    "ukmo_seamless",
    "ecmwf_ifs025",
    "bom_access_global",
    "gem_seamless",
    "knmi_seamless",
]

# Le stesse identiche variabili che l'app chiede, copiate da
# `WeatherRepository.HOURLY_VARS` e `DAILY_VARS`.
#
# Copiate e non riassunte: il giro serve a sapere se **quello che l'app
# chiede** torna, e una lista piu' corta darebbe una risposta a una domanda
# che nessuno ha fatto. Se le due divergono, il controllo qui sotto se ne
# accorge dal numero di colonne e lo dice.
APP_HOURLY = (
    "temperature_2m,apparent_temperature,weather_code,precipitation,"
    "precipitation_probability,is_day,"
    "relative_humidity_2m,dew_point_2m,wind_speed_10m,wind_gusts_10m,"
    "wind_direction_10m,uv_index,cloud_cover,surface_pressure,visibility,"
    "rain,snowfall"
)

APP_DAILY = (
    "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,"
    "apparent_temperature_min,precipitation_sum,precipitation_probability_max,"
    "wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant,uv_index_max,"
    "relative_humidity_2m_mean,dew_point_2m_mean,precipitation_hours,"
    "rain_sum,snowfall_sum,sunshine_duration,sunrise,sunset"
)

CANDIDATES = [
    "best_match",
    "italia_meteo_arpae_icon_2i",
    "icon_d2",
    "icon_eu",
    "icon_seamless",
    "ecmwf_ifs025",
    "ecmwf_aifs025_single",
    "meteofrance_seamless",
    "arpae_cosmo_5m",
    "gfs_seamless",
    "knmi_seamless",
    "ukmo_seamless",
]

OUT = "/tmp/ciout"


def fetch(url: str, timeout: int = 30):
    """Torna (codice, tipo, corpo). L'errore non e' un'eccezione: e' una risposta."""
    request = urllib.request.Request(url, headers={"User-Agent": "test-weather-probe"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            kind = response.headers.get("Content-Type", "?")
            return response.status, kind, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        kind = error.headers.get("Content-Type", "?") if error.headers else "?"
        return error.code, kind, error.read().decode("utf-8", "replace")
    except Exception as error:  # rete assente, DNS, timeout
        return 0, "?", f"{type(error).__name__}: {error}"


def describe(model: str, name: str, lat: float, lon: float) -> str:
    url = (
        f"{BASE}?latitude={lat}&longitude={lon}"
        f"&hourly=temperature_2m&forecast_days=2&models={model}"
    )
    code, kind, body = fetch(url)
    if code != 200:
        short = body.replace("\n", " ")[:160]
        return f"    {name:9s} HTTP {code}  {short}"
    try:
        data = json.loads(body)
    except ValueError:
        # Il caso peggiore per chi scrive il client, e quindi quello da
        # descrivere per esteso: la richiesta **riesce**, ma quello che torna
        # non e' quello che il client si aspetta di deserializzare.
        short = body.replace("\n", " ")[:200]
        return (
            f"    {name:9s} HTTP 200 ma NON JSON  tipo={kind}  "
            f"lunghezza={len(body)}  corpo=[{short}]"
        )

    hourly = data.get("hourly", {})
    series = next((v for k, v in hourly.items() if k != "time"), [])
    good = [v for v in series if v is not None]
    elevation = data.get("elevation")
    if not good:
        return f"    {name:8s} accettato ma SENZA DATI qui (quota {elevation})"
    return (
        f"    {name:8s} {len(good)}/{len(series)} ore  quota {elevation} m  "
        f"prime {good[:3]}"
    )


def fingerprint(model: str, lat: float, lon: float) -> list | None:
    """Le prime ore di temperatura di un modello, per riconoscerlo dai numeri.

    Torna nulla se il modello non risponde o non ha valori qui: entrambe le
    cose vogliono dire "non e' lui", ed e' quello che al chiamante serve.
    """
    url = (
        f"{BASE}?latitude={lat}&longitude={lon}"
        f"&hourly=temperature_2m&forecast_days=2&models={model}"
    )
    code, _, body = fetch(url)
    if code != 200:
        return None
    try:
        series = json.loads(body).get("hourly", {}).get("temperature_2m", [])
    except ValueError:
        return None
    head = series[:6]
    return head if head and all(v is not None for v in head) else None


def who_answered(name: str, lat: float, lon: float) -> str:
    """Chi c'e' davvero dietro `best_match`, dedotto e non creduto.

    Open-Meteo il nome del modello scelto non lo dichiara in nessun campo
    della risposta. Si confrontano quindi i numeri: si chiede la stessa ora a
    ciascun candidato nazionale e si guarda **chi da' gli stessi identici
    decimali**. Due modelli diversi non coincidono per caso su sei ore.

    Puo' non riconoscerne nessuno, e non e' un guasto: vuol dire che sotto c'e'
    un modello che non sta fra i candidati, o una fusione di piu' d'uno. Si
    dice cosi', invece di attribuirla al piu' somigliante.
    """
    reference = fingerprint("best_match", lat, lon)
    if reference is None:
        return f"  {name:14s} best_match NON RISPONDE qui"
    matches = [m for m in NATIONAL if fingerprint(m, lat, lon) == reference]
    if not matches:
        return f"  {name:14s} best_match = ? (nessun candidato coincide)"
    return f"  {name:14s} best_match = {', '.join(matches)}"


def coverage(name: str, lat: float, lon: float) -> list:
    """Quante ore e quanti giorni tornano, e **quali colonne sono vuote**.

    E' la meta' piu' utile del giro. Una variabile che l'API accetta ma non
    riempie torna come una colonna di `null`, non come un errore: chi la
    disegna ci vede una linea a zero, cioe' una previsione di niente invece
    di un "non lo so". La curva della probabilita' sulla scheda della pioggia
    e' esattamente questo caso.
    """
    url = (
        f"{BASE}?latitude={lat}&longitude={lon}&forecast_days=7"
        f"&hourly={APP_HOURLY}&daily={APP_DAILY}&timezone=auto"
    )
    code, _, body = fetch(url)
    if code != 200:
        short = body.replace("\n", " ")[:160]
        return [f"  {name:14s} HTTP {code}  {short}"]
    try:
        data = json.loads(body)
    except ValueError:
        return [f"  {name:14s} HTTP 200 ma NON JSON"]

    out = []
    for label, block, asked in (
        ("ore", data.get("hourly", {}), APP_HOURLY),
        ("giorni", data.get("daily", {}), APP_DAILY),
    ):
        wanted = asked.split(",")
        missing = [v for v in wanted if v not in block]
        empty = []
        for key, series in block.items():
            if key == "time" or not isinstance(series, list):
                continue
            if not any(v is not None for v in series):
                empty.append(key)
        span = len(block.get("time", []))
        out.append(
            f"  {name:14s} {label:6s} {span:3d}  "
            f"assenti={missing or '-'}  tutte nulle={empty or '-'}"
        )
    return out


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    lines = []

    # 1. L'elenco ufficiale, chiesto sbagliando apposta.
    lines.append("=== modelli accettati (dall'errore su un nome inventato) ===")
    _, _, body = fetch(f"{BASE}?latitude=44.8&longitude=10.18&hourly=temperature_2m&models=zzz")
    lines.append(body.strip()[:4000])
    lines.append("")

    # 2. Chi ha davvero dei valori sopra i due punti.
    lines.append("=== copertura reale sui due punti ===")
    for model in CANDIDATES:
        lines.append(f"  {model}")
        for name, lat, lon in PLACES:
            lines.append(describe(model, name, lat, lon))
    lines.append("")

    # 3. Cosa succede a un modello regionale fuori casa.
    lines.append("=== modelli regionali fuori dal loro dominio ===")
    for model in REGIONAL:
        lines.append(f"  {model}")
        for name, lat, lon in ABROAD:
            lines.append(describe(model, name, lat, lon))
    lines.append("")

    # 4. Il giro mondiale: chi risponde dove, e cosa non torna.
    #
    # Sta in fondo perche' e' la parte lunga - undici punti per undici modelli
    # sono un centinaio di richieste - e se la rete cade a meta' le tre sezioni
    # sopra sono gia' scritte.
    lines.append("=== chi risponde sotto best_match, nel mondo ===")
    for name, lat, lon in WORLD:
        lines.append(who_answered(name, lat, lon))
    lines.append("")

    lines.append("=== cosa torna e cosa manca, con le variabili che l'app chiede ===")
    for name, lat, lon in WORLD:
        lines.extend(coverage(name, lat, lon))
    lines.append("")

    report = "\n".join(lines)
    with open(os.path.join(OUT, "modelli.txt"), "w", encoding="utf-8") as handle:
        handle.write(report + "\n")
    print(report)


if __name__ == "__main__":
    main()

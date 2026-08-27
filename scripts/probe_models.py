#!/usr/bin/env python3
"""Chiede a Open-Meteo quali modelli esistono e quali coprono davvero l'Italia.

Serve perche' la domanda "quale modello e' piu' preciso qui" non si risponde a
memoria: i modelli vengono aggiunti e ritirati, e un modello ad alta risoluzione
**accettato** non e' lo stesso di un modello che ha davvero dei valori sopra
Noceto. Il container di sviluppo non raggiunge api.open-meteo.com, la CI si:
questo gira li' e pubblica il risultato su ci-artifacts.

La chiamata piu' informativa e' la prima: si chiede un modello inesistente
apposta, e l'errore che torna elenca tutti quelli accettati.
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

    report = "\n".join(lines)
    with open(os.path.join(OUT, "modelli.txt"), "w", encoding="utf-8") as handle:
        handle.write(report + "\n")
    print(report)


if __name__ == "__main__":
    main()

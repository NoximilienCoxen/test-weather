#!/usr/bin/env python3
"""
Open-Meteo sa rispondere per piu' punti in una volta sola?

**E' la domanda che decide se la carta della pioggia serva a qualcosa.** Il
radar di RainViewer tiene due ore di storico: alle 17:06 risponde dalle 15:10
alle 17:00, cioe' tre ore su ventiquattro della barra. Chi usa l'app ha scorso
fino alle 11:00, ha trovato "niente per quest'ora" ed e' arrivato alla
conclusione giusta - una mappa che risponde per tre ore non e' una mappa.

Un radar misura, e quello che non ha misurato non lo sa: questo non si aggira.
Ma il **modello** una risposta per ogni ora ce l'ha - e' lo stesso Open-Meteo
che riempie le dodici colonne sopra la carta, solo chiesto su una griglia di
punti invece che su uno. Non sarebbe radar e non andrebbe chiamato radar:
sarebbe una previsione, e andrebbe disegnata come una previsione.

Prima di scriverne una riga servono tre risposte, e CONTESTO 18 sta li' a
ricordare cosa costa non chiederle:

1. accetta piu' coordinate in una richiesta, e fino a quante?
2. che forma ha la risposta - una lista di risposte, o un oggetto solo?
3. quanto pesa una griglia utile per ventiquattro ore?
"""
import json
import subprocess
import sys

CASA = (44.2226, 12.0407)


def griglia(lato, passoLat=0.42, passoLon=0.60):
    """Una griglia quadrata attorno a casa, larga quanto la finestra della carta."""
    lat, lon = [], []
    for r in range(lato):
        for c in range(lato):
            lat.append(f"{CASA[0] + (r - lato // 2) * passoLat:.4f}")
            lon.append(f"{CASA[1] + (c - lato // 2) * passoLon:.4f}")
    return ",".join(lat), ",".join(lon)


def prova(lato):
    lat, lon = griglia(lato)
    url = (
        "https://api.open-meteo.com/v1/forecast"
        f"?latitude={lat}&longitude={lon}"
        "&hourly=precipitation&forecast_days=2&timezone=UTC"
    )
    print(f"--- griglia {lato}x{lato} = {lato * lato} punti")
    print(f"    url lungo {len(url)} caratteri")
    fuori = subprocess.run(
        ["curl", "-sS", "--max-time", "45", "-w", "\\n%{http_code} %{size_download}", url],
        capture_output=True,
        text=True,
    )
    corpo = fuori.stdout
    coda = corpo.rsplit("\n", 1)
    testo, stato = (coda[0], coda[1]) if len(coda) == 2 else (corpo, "?")
    print(f"    HTTP {stato}")
    try:
        d = json.loads(testo)
    except Exception as e:
        print(f"    non e' JSON: {e}; primi byte: {testo[:160]}")
        return
    if isinstance(d, list):
        ore = len(d[0]["hourly"]["time"])
        print(f"    LISTA di {len(d)} risposte, {ore} ore ciascuna")
        print(f"    punto 0, prime tre ore: {d[0]['hourly']['precipitation'][:3]}")
        print(f"    punto 0 sta a {d[0].get('latitude')},{d[0].get('longitude')}")
    elif "error" in d or d.get("reason"):
        print(f"    RIFIUTATA: {d.get('reason') or d}")
    else:
        print(f"    oggetto solo (un punto), chiavi: {list(d.keys())[:8]}")


def main():
    for lato in (2, 7, 10, 12):
        prova(lato)
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())

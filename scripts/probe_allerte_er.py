#!/usr/bin/env python3
"""Le allerte regionali dell'Emilia-Romagna, interrogate davvero.

MeteoAlarm per l'Italia riprende gli avvisi nazionali, e il 26 novembre 2026 a
Fontevivo (PR) non portava l'allerta vento che il sistema regionale aveva
emesso (CONTESTO §85). Prima di proporre una seconda fonte si guarda cosa
pubblica Allerta Meteo Emilia-Romagna:

- se ci sono dati aperti **per zona di allerta** (Fontevivo sta in H2) o solo
  i PDF dei bollettini;
- come si legge il **colore per fenomeno**, vento compreso.

Da dentro il container di sviluppo il sito non si raggiunge (403 al CONNECT),
quindi la domanda si fa qui. Lo script non conosce in anticipo le API del
sito: scarica la pagina principale e quella delle allerte, raccoglie dai
sorgenti e dagli script gli indirizzi che sembrano dati (`/o/api/`, `json`,
`geojson`, `rss`, `xml`, `cap`, `pdf`) e li chiede uno per uno. Prova anche
il portale dei dati aperti della Regione e il repository dei bollettini della
Protezione Civile nazionale.

Ogni risposta grezza va in `allerte-er/<nome>`, e in `allerte-er/INDICE.txt`
stato HTTP, tipo, byte e le prime righe. Non fa fallire il giro.
"""

import json
import os
import time
import re
import urllib.error
import urllib.parse
import urllib.request

OUT = os.environ.get("OUT", "/tmp/ciout/allerte-er")
# Il sito puo' rispondere a goccia: ogni richiesta ha 20 secondi, e la sonda
# intera non piu' di otto minuti. Quello che resta fuori lo dice l'indice.
SCADENZA = time.monotonic() + 8 * 60
AGENTE = "Caelum/1.0 (+https://github.com/NoximilienCoxen/test-weather)"
SITO = "https://allertameteo.regione.emilia-romagna.it"

PAGINE_LETTE = [
    # Secondo giro (27 novembre): la guida per gli sviluppatori e le note
    # legali sono linkate dal pie' di pagina; vanno lette per prime.
    ("sviluppatori", SITO + "/sviluppatori"),
    ("note-legali", SITO + "/note-legali"),
    ("home", SITO + "/"),
    ("allerte-bollettini", SITO + "/allerte-e-bollettini"),
    ("allerte-bollettini-2", SITO + "/web/guest/allerte-e-bollettini"),
    ("open-data", SITO + "/open-data"),
    ("dati-aperti", SITO + "/dati-aperti"),
    ("zone-allerta", SITO + "/zone-di-allerta"),
]
# Le pagine sono gia' state lette (CONTESTO §85.1): al terzo giro solo i servizi.
PAGINE = []

# Indirizzi da provare comunque: alcuni li ho visti citati, altri sono
# tentativi ragionevoli. Un 404 nell'indice e' una risposta anche lui.
TENTATIVI = [
    # Terzo giro: i servizi della guida per gli sviluppatori, con la data del
    # caso di Fontevivo (26 novembre, Allerta 119/2026 con "vento"), e i dati
    # dei comuni che il sito usa per "Cerca un comune".
    ("doc-stato-26nov", SITO + "/o/get-stato-allerta?data=2026-11-26%2012:00"),
    ("doc-stato-adesso", SITO + "/o/get-stato-allerta"),
    ("doc-cap-26nov", SITO + "/o/xmlcap?data=2026-11-26%2012:00"),
    ("js-comuni-new", SITO + "/o/allerta-frontend-js/js/am.data.comuni-new.js?v=1.4"),
    ("js-aree-idro-new", SITO + "/o/allerta-frontend-js/js/am.data.aree-idro-new.js?v=1.3"),
    # Il feed RSS linkato dalla pagina delle allerte.
    ("rss-allerte-bollettini", SITO + "/o/compila-allerta-portlet/feed?feed=allerte-bollettini"),
    # Lo stato per zona trovato al primo giro, e lo stesso con una data: la
    # risposta non dice a che giorno si riferisce.
    ("api-stato-allerta-oggi", SITO + "/o/api/allerta/get-stato-allerta"),
    ("api-stato-allerta-data", SITO + "/o/api/allerta/get-stato-allerta?data=2026-11-26"),
    ("api-stato-allerta-domani", SITO + "/o/api/allerta/get-stato-allerta?giorno=domani"),
    ("api-allerte", SITO + "/o/api/allerta/get-allerte"),
    ("api-allerta-oggi", SITO + "/o/api/allerta/get-allerta-oggi"),
    ("api-stato-allerta", SITO + "/o/api/allerta/get-stato-allerta"),
    ("api-bollettini", SITO + "/o/api/allerta/get-bollettini"),
    ("api-documenti", SITO + "/o/api/allerta/get-documenti"),
    ("api-zone", SITO + "/o/api/allerta/get-zone"),
    ("rss-allerte", SITO + "/rss/allerte"),
    ("rss", SITO + "/rss"),
    # Portale dei dati aperti della Regione (CKAN).
    ("ckan-allerta", "https://dati.emilia-romagna.it/api/3/action/package_search?q=allerta&rows=20"),
    ("ckan-zone", "https://dati.emilia-romagna.it/api/3/action/package_search?q=zone%20di%20allerta&rows=20"),
    # Protezione Civile nazionale: i bollettini di criticita' per zona, in
    # chiaro su GitHub. Non hanno il vento, ma hanno le zone.
    ("dpc-repo", "https://api.github.com/repos/pcm-dpc/DPC-Bollettini-Criticita-Idrogeologica-Idraulica/contents/"),
    ("dpc-avvisi-repo", "https://api.github.com/search/repositories?q=org:pcm-dpc"),
]

PAROLE = re.compile(r"(/o/api/|\.json|geojson|/rss|\.xml|/cap|\.pdf|allert|bollettin|zone)", re.I)
URL = re.compile(r"""(?:href|src|action|data-url|url)\s*[=:]\s*["']([^"'<>\s]+)["']|["'](/o/[^"'<>\s]+)["']|(https?://[^"'<>\s)]+)""", re.I)


def chiedi(url, accept="application/json, application/xml;q=0.9, text/html;q=0.8, */*;q=0.5"):
    if time.monotonic() > SCADENZA:
        return None, "saltato: sonda oltre gli otto minuti", b""
    req = urllib.request.Request(url, headers={"User-Agent": AGENTE, "Accept": accept})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, r.headers.get("Content-Type", ""), r.read(20_000_000)
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Content-Type", "") if e.headers else "", e.read(20000) if e.fp else b""
    except Exception as e:  # rete, TLS, timeout: va nell'indice anche questo
        return None, type(e).__name__ + ": " + str(e), b""


def salva(nome, corpo):
    nome = re.sub(r"[^A-Za-z0-9._-]+", "_", nome)[:120]
    with open(os.path.join(OUT, nome), "wb") as f:
        f.write(corpo)
    return nome


def anteprima(corpo):
    testo = corpo[:400].decode("utf-8", "replace")
    return " ".join(testo.split())[:300]


def main():
    os.makedirs(OUT, exist_ok=True)
    indice = []
    trovati = []

    def registra(nome, url, stato, tipo, corpo):
        file = salva(nome, corpo) if corpo else "-"
        indice.append(f"{nome}\t{stato}\t{tipo}\t{len(corpo)} byte\t{url}\t{file}\n    {anteprima(corpo)}")

    for nome, url in PAGINE:
        stato, tipo, corpo = chiedi(url, "text/html, */*;q=0.8")
        registra(nome + ".html", url, stato, tipo, corpo)
        testo = corpo.decode("utf-8", "replace")
        for m in URL.finditer(testo):
            u = next(g for g in m.groups() if g)
            if PAROLE.search(u):
                trovati.append(urllib.parse.urljoin(url, u))
        # Gli script del sito: le chiamate alle API stanno spesso li' dentro.
        for js in re.findall(r"""<script[^>]+src=["']([^"']+)["']""", testo)[:10]:
            js = urllib.parse.urljoin(url, js)
            if not js.startswith(SITO):
                continue
            s, t, c = chiedi(js, "*/*")
            for u in re.findall(r"""["'`](/o/api/[^"'`\s]+|/api/[^"'`\s]+)["'`]""", c.decode("utf-8", "replace")):
                trovati.append(urllib.parse.urljoin(SITO, u))

    visti = set()
    unici = []
    for u in trovati:
        u = u.split("#")[0]
        if re.search(r"\.(css|png|svg|ico|jpg|gif|woff2?)(\?|$)", u, re.I):
            continue
        if u not in visti and ("emilia-romagna" in u or "protezionecivile" in u):
            visti.add(u)
            unici.append(u)

    with open(os.path.join(OUT, "INDIRIZZI-TROVATI.txt"), "w") as f:
        f.write("\n".join(unici))

    # Prima i tentativi fissi, poi quello che il sito stesso indica (al
    # massimo sessanta, i PDF solo per l'intestazione).
    da_chiedere = list(TENTATIVI)[:5] + [(f"trovato-{i:02d}", u) for i, u in enumerate(unici[:0])]
    for nome, url in da_chiedere:
        stato, tipo, corpo = chiedi(url)
        if "pdf" in tipo.lower():
            corpo = corpo[:2000]
        est = ".json" if "json" in tipo else ".xml" if "xml" in tipo else ".pdf" if "pdf" in tipo else ".html" if "html" in tipo else ".txt"
        registra(nome + est, url, stato, tipo, corpo)
        # Una risposta JSON si riassume: le chiavi dicono se c'e' il colore
        # per zona e per fenomeno.
        if "json" in tipo and corpo:
            try:
                dato = json.loads(corpo)
                with open(os.path.join(OUT, nome + ".chiavi.txt"), "w") as f:
                    f.write(chiavi(dato))
            except Exception:
                pass

    with open(os.path.join(OUT, "INDICE.txt"), "w") as f:
        f.write("\n".join(indice) + "\n")
    print("\n".join(indice))


def chiavi(dato, prefisso="", profondita=0):
    if profondita > 5:
        return ""
    righe = []
    if isinstance(dato, dict):
        for k, v in list(dato.items())[:40]:
            righe.append(f"{prefisso}{k}: {type(v).__name__} {str(v)[:80] if not isinstance(v, (dict, list)) else ''}")
            righe.append(chiavi(v, prefisso + "  ", profondita + 1))
    elif isinstance(dato, list) and dato:
        righe.append(f"{prefisso}[{len(dato)} voci]")
        righe.append(chiavi(dato[0], prefisso + "  ", profondita + 1))
    return "\n".join(r for r in righe if r)


if __name__ == "__main__":
    main()

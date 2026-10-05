#!/usr/bin/env python3
"""Le allerte meteo fuori dall'area MeteoAlarm, interrogate davvero.

MeteoAlarm copre l'Europa e basta (CONTESTO §8-ter). Prima di scrivere un
lettore per un'altra fonte si guarda cosa risponde: la prima stesura del
parser MeteoAlarm cercava `awareness_level`, che nel feed non esiste, e il
406 di ottobre 2026 e' rimasto nascosto perche' la sonda usava curl e non le
intestazioni dell'app. Da qui le due regole di questo script:

- ogni fonte si chiede **con le intestazioni che userebbe l'app**
  (`User-Agent: Caelum/1.0 ...`, un `Accept` coi tipi preferiti e il jolly);
- la risposta grezza si salva in `allerte-mondo/<nome>.<ext>`, e in
  `allerte-mondo/INDICE.txt` vanno stato HTTP, tipo, byte, quante voci e i
  campi CAP che compaiono davvero.

Le fonti sono quelle che i servizi meteorologici nazionali pubblicano senza
chiave, scelte dove l'app ha localita' suggerite o utenti probabili: Regno
Unito (dentro MeteoAlarm, ma va verificato che il feed abbia voci), Stati
Uniti, Canada, Nuova Zelanda, Giappone, Australia. Gli indirizzi vengono
dalla documentazione dei servizi, non da un elenco di terze parti; se uno
risponde 404 lo dice l'indice, ed e' per questo che la sonda esiste.

Non fa fallire il giro: e' una ricognizione, non un contratto.
"""

import os
import re
import urllib.error
import urllib.request

OUT = os.environ.get("OUT", "/tmp/ciout/allerte-mondo")
AGENTE = "Caelum/1.0 (+https://github.com/NoximilienCoxen/test-weather)"

# nome, indirizzo, Accept, estensione del file grezzo
FONTI = [
    # Regno Unito: il Met Office e' membro di EUMETNET, e l'app lo mappa gia'
    # su `united-kingdom`. La domanda e' se il feed porta voci vere.
    ("uk-meteoalarm",
     "https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-united-kingdom",
     "application/atom+xml, application/xml;q=0.9, */*;q=0.8", "xml"),
    # Il feed RSS degli avvisi nazionali del Met Office, per tutto il Regno Unito.
    ("uk-metoffice-rss",
     "https://www.metoffice.gov.uk/public/data/PWSCache/WarningsRSS/Region/UK",
     "application/rss+xml, application/xml;q=0.9, */*;q=0.8", "xml"),
    # Stati Uniti, National Weather Service: un punto (New York) e il
    # conteggio nazionale, in GeoJSON e in CAP.
    ("us-nws-punto-geojson",
     "https://api.weather.gov/alerts/active?point=40.7128,-74.0060",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    ("us-nws-punto-cap",
     "https://api.weather.gov/alerts/active?point=40.7128,-74.0060",
     "application/cap+xml, application/xml;q=0.9, */*;q=0.8", "xml"),
    # Il punto di New York il 5 ottobre era vuoto: per vedere la forma di una
    # voce vera servono stati con allerte attive in quel momento. Si chiedono
    # i piu' grandi, e l'indice dice quanti `features` ha ciascuno.
    ("us-nws-area-fl",
     "https://api.weather.gov/alerts/active?area=FL",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    ("us-nws-area-ca",
     "https://api.weather.gov/alerts/active?area=CA",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    ("us-nws-area-ak",
     "https://api.weather.gov/alerts/active?area=AK",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    ("us-nws-conteggio",
     "https://api.weather.gov/alerts/active/count",
     "application/json, */*;q=0.8", "json"),
    # Canada, Environment and Climate Change Canada: l'API OGC di GeoMet.
    ("ca-eccc-ogc",
     "https://api.weather.gc.ca/collections/weather-alerts/items?f=json&limit=3",
     "application/geo+json, application/json;q=0.9, */*;q=0.8", "json"),
    # Nuova Zelanda, MetService: il feed CAP pubblico (Aoraki e' fra i
    # suggerimenti dell'app).
    ("nz-metservice-cap",
     "https://alerts.metservice.com/cap/rss",
     "application/rss+xml, application/xml;q=0.9, */*;q=0.8", "xml"),
    # Giappone, JMA: il feed Atom dei bollettini XML e gli avvisi per Tokyo.
    ("jp-jma-feed",
     "https://www.data.jma.go.jp/developer/xml/feed/extra.xml",
     "application/atom+xml, application/xml;q=0.9, */*;q=0.8", "xml"),
    ("jp-jma-tokyo",
     "https://www.jma.go.jp/bosai/warning/data/warning/130000.json",
     "application/json, */*;q=0.8", "json"),
    # Australia, Bureau of Meteorology: gli avvisi del Victoria in XML.
    ("au-bom-vic",
     "https://www.bom.gov.au/fwo/IDZ00059.warnings_vic.xml",
     "application/xml, */*;q=0.8", "xml"),
]

# Cio' che si conta in ogni risposta: dice se ci sono voci e in che forma.
SEGNI = [
    "<entry", "<item", "<alert", "cap:event", "<event>", "cap:severity",
    "<severity>", "areaDesc", "\"features\"", "\"event\"", "\"severity\"",
    "\"areaDesc\"", "\"onset\"", "\"expires\"", "senderName", "\"senderName\"",
]


def chiedi(url, accept):
    req = urllib.request.Request(url, headers={"User-Agent": AGENTE, "Accept": accept})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.headers.get("Content-Type", ""), r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Content-Type", ""), e.read()
    except Exception as e:  # rete, TLS, tempo scaduto
        return 0, "", f"{type(e).__name__}: {e}".encode()


def main():
    os.makedirs(OUT, exist_ok=True)
    righe = []
    for nome, url, accept, ext in FONTI:
        code, tipo, corpo = chiedi(url, accept)
        with open(os.path.join(OUT, f"{nome}.{ext}"), "wb") as f:
            f.write(corpo)
        testo = corpo.decode("utf-8", errors="replace")
        righe.append(f"== {nome}")
        righe.append(f"   {url}")
        righe.append(f"   Accept: {accept}")
        righe.append(f"   HTTP {code}  tipo={tipo}  byte={len(corpo)}")
        conti = [f"{s}={testo.count(s)}" for s in SEGNI if s in testo]
        if ext == "json" and "\"features\"" in testo:
            try:
                import json
                conti.append(f"voci={len(json.loads(testo).get('features', []))}")
            except Exception as e:
                conti.append(f"json illeggibile: {e}")
        righe.append("   " + ("  ".join(conti) if conti else "nessun segno noto"))
        # I primi eventi, per vedere come si chiamano davvero.
        eventi = re.findall(r"<(?:cap:)?event>([^<]{1,80})<", testo)[:5]
        eventi += re.findall(r"\"event\"\s*:\s*\"([^\"]{1,80})\"", testo)[:5]
        if eventi:
            righe.append("   eventi: " + " | ".join(eventi))
        righe.append("   inizio: " + re.sub(r"\s+", " ", testo[:400]))
        righe.append("")
    indice = "\n".join(righe)
    with open(os.path.join(OUT, "INDICE.txt"), "w") as f:
        f.write(indice + "\n")
    print(indice)


if __name__ == "__main__":
    main()
